package com.agent.dock.budget.service;

import com.agent.dock.budget.domain.BudgetPeriod;
import com.agent.dock.budget.domain.ProjectBudget;
import com.agent.dock.budget.domain.UsageInterval;
import com.agent.dock.budget.dto.ProjectBudgetResponse;
import com.agent.dock.budget.dto.UpdateBudgetRequest;
import com.agent.dock.budget.dto.UsageResponse;
import com.agent.dock.budget.repository.ProjectBudgetRepository;
import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.execution.repository.ExecutionUsagePoint;
import com.agent.dock.notification.domain.NotificationEventType;
import com.agent.dock.notification.service.NotificationService;
import com.agent.dock.project.domain.Project;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.task.repository.TaskRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * 프로젝트 비용 예산과 기간별 사용량 집계를 맡는다.
 *
 * <p>사용량은 실행(execution)의 계측값(cost_usd·토큰)을 기간 창으로 걸러 더한다 — 0 으로 지어내지 않고
 * CLI 가 준 값만 쓴다. 예산은 프로젝트당 하나이고, 기간(일/주/월)별 창(window)의 사용액이
 * alertRatio(기본 80%)를 넘으면 알림 규칙(BUDGET_THRESHOLD)으로 경고를 한 번 보낸다(같은 기간 재발송 없음).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    /** alertRatio 를 주지 않았을 때의 기본값(80% 도달 경고). */
    static final BigDecimal DEFAULT_ALERT_RATIO = new BigDecimal("0.80");
    /** 사용량 기본 조회 창(끝에서 이만큼 거슬러). */
    static final Duration DEFAULT_USAGE_WINDOW = Duration.ofDays(30);
    /** 한 번에 만들 수 있는 버킷 상한. 넘으면 400(기간을 좁히라). */
    static final int MAX_BUCKETS = 1000;

    private final ProjectBudgetRepository budgetRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ExecutionRepository executionRepository;
    /** 예산 경고를 알림 규칙을 통해 내보낸다(채널·규칙은 notification 도메인). */
    private final NotificationService notificationService;
    /** 전역 SSE 스트림에 "예산이 바뀌었다"를 알린다. */
    private final EventPublisher changeEvents;
    /** 예산과 이번 기간 사용 현황. 예산을 정하지 않았으면 configured=false 로 돌려준다(404 아님). */
    public ProjectBudgetResponse budget(Long projectId) {
        requireProject(projectId);
        Instant now = Instant.now();
        ProjectBudget budget = budgetRepository.findByProjectId(projectId).orElse(null);
        BudgetPeriod period = budget == null ? BudgetPeriod.MONTHLY : budget.getPeriod();
        Window window = window(period, now);
        Usage usage = usageOf(projectId, window.from(), window.to());
        if (budget == null) {
            return new ProjectBudgetResponse(projectId, false, null, null, DEFAULT_ALERT_RATIO,
                    usage.cost(), null, false, window.from(), window.to(),
                    usage.count(), usage.inputTokens(), usage.outputTokens());
        }
        BigDecimal ratio = ratio(usage.cost(), budget.getAmountUsd());
        boolean warning = ratio != null && ratio.compareTo(alertRatioOf(budget)) >= 0;
        return new ProjectBudgetResponse(projectId, true, budget.getAmountUsd(), budget.getPeriod(),
                alertRatioOf(budget), usage.cost(), ratio, warning, window.from(), window.to(),
                usage.count(), usage.inputTokens(), usage.outputTokens());
    }

    /** 예산을 새로 만들거나 고친다(프로젝트당 하나). 바뀌면 전역 스트림에 알린다. */
    @Transactional
    public ProjectBudgetResponse update(Long projectId, UpdateBudgetRequest request) {
        Project project = requireProject(projectId);
        ProjectBudget budget = budgetRepository.findByProjectId(projectId).orElseGet(() -> {
            ProjectBudget created = new ProjectBudget();
            created.setProject(project);
            return created;
        });
        budget.setAmountUsd(request.amountUsd().setScale(2, RoundingMode.HALF_UP));
        budget.setPeriod(request.period() == null ? BudgetPeriod.MONTHLY : request.period());
        budget.setAlertRatio(request.alertRatio() == null ? DEFAULT_ALERT_RATIO
                : request.alertRatio().setScale(3, RoundingMode.HALF_UP));
        budgetRepository.save(budget);
        changeEvents.budgetChanged(projectId);
        return budget(projectId);
    }

    /** 예산 삭제(없어도 204 - 멱등). 프로젝트가 없으면 404. */
    @Transactional
    public void delete(Long projectId) {
        requireProject(projectId);
        budgetRepository.findByProjectId(projectId).ifPresent(budgetRepository::delete);
        changeEvents.budgetChanged(projectId);
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(projectId)));
    }
    /** 기간별 사용량 집계(projectId 없으면 전역). from/to 는 ISO-8601 Instant, bucket 은 DAY/WEEK/MONTH(기본 DAY). */
    public UsageResponse usage(Long projectId, String fromParam, String toParam, String bucketParam) {
        Instant to = parseInstant(toParam, Instant.now());
        Instant from = parseInstant(fromParam, to.minus(DEFAULT_USAGE_WINDOW));
        if (!from.isBefore(to)) {
            throw new BadRequestException("from 은 to 보다 앞서야 합니다");
        }
        UsageInterval interval = parseInterval(bucketParam);
        List<ExecutionUsagePoint> points;
        if (projectId != null) {
            requireProject(projectId);
            List<Long> taskIds = taskRepository.findIdsByProjectId(projectId);
            points = taskIds.isEmpty() ? List.of()
                    : executionRepository.findUsagePointsByTaskIds(taskIds, from, to);
        } else {
            points = executionRepository.findUsagePointsGlobal(from, to);
        }
        return aggregate(projectId, from, to, interval, points);
    }
    private UsageResponse aggregate(Long projectId, Instant from, Instant to, UsageInterval interval,
                                    List<ExecutionUsagePoint> points) {
        List<Instant> starts = bucketStarts(from, to, interval);
        Map<Instant, Usage> byStart = new HashMap<>();
        Usage total = Usage.zero();
        for (ExecutionUsagePoint point : points) {
            Instant start = interval.start(point.createdAt());
            byStart.put(start, byStart.getOrDefault(start, Usage.zero()).plus(point));
            total = total.plus(point);
        }
        List<UsageResponse.UsageBucket> buckets = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            Instant start = starts.get(i);
            Instant end = i + 1 < starts.size() ? starts.get(i + 1) : to;
            Usage usage = byStart.getOrDefault(start, Usage.zero());
            buckets.add(new UsageResponse.UsageBucket(start, end, usage.count(), usage.cost(),
                    usage.inputTokens(), usage.outputTokens()));
        }
        return new UsageResponse(projectId, from, to, interval.name(), total.cost(),
                total.inputTokens(), total.outputTokens(), total.count(), buckets);
    }

    /** from(정렬)부터 to 전까지의 버킷 시작 시각들. 기간이 너무 길면 400. */
    private static List<Instant> bucketStarts(Instant from, Instant to, UsageInterval interval) {
        List<Instant> starts = new ArrayList<>();
        Instant cursor = interval.start(from);
        while (cursor.isBefore(to)) {
            if (starts.size() >= MAX_BUCKETS) {
                throw new BadRequestException("기간이 너무 깁니다(버킷 %d개 초과)".formatted(MAX_BUCKETS));
            }
            starts.add(cursor);
            cursor = interval.next(cursor);
        }
        if (starts.isEmpty()) {
            starts.add(interval.start(from));
        }
        return starts;
    }
    private static Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("잘못된 시각입니다(ISO-8601): " + value);
        }
    }

    private static UsageInterval parseInterval(String value) {
        if (value == null || value.isBlank()) {
            return UsageInterval.DAY;
        }
        try {
            return UsageInterval.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("알 수 없는 버킷입니다(DAY/WEEK/MONTH): " + value);
        }
    }

    /** 기간 창 안의 사용량 합(그 프로젝트 Task 들의 실행만). */
    private Usage usageOf(Long projectId, Instant from, Instant to) {
        List<Long> taskIds = taskRepository.findIdsByProjectId(projectId);
        if (taskIds.isEmpty()) {
            return Usage.zero();
        }
        Usage usage = Usage.zero();
        for (ExecutionUsagePoint point : executionRepository.findUsagePointsByTaskIds(taskIds, from, to)) {
            usage = usage.plus(point);
        }
        return usage;
    }
    /**
     * 예산별로 이번 기간 사용액을 보고, alertRatio 를 넘었으면 아직 알리지 않은 기간일 때만 경고를 보낸다.
     * 15분마다 돈다(요청 스레드와 무관). 같은 기간에는 alertedPeriod 로 한 번만 보낸다.
     */
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
    public int evaluateWarnings() {
        Instant now = Instant.now();
        int fired = 0;
        for (ProjectBudget budget : budgetRepository.findAll()) {
            Window window = window(budget.getPeriod() == null ? BudgetPeriod.MONTHLY : budget.getPeriod(), now);
            if (window.key().equals(budget.getAlertedPeriod())) {
                continue;
            }
            Usage usage = usageOf(budget.getProjectId(), window.from(), window.to());
            BigDecimal ratio = ratio(usage.cost(), budget.getAmountUsd());
            if (ratio == null || ratio.compareTo(alertRatioOf(budget)) < 0) {
                continue;
            }
            budget.setAlertedPeriod(window.key());
            budgetRepository.save(budget);
            String subject = "[AgentDock] 예산 경고 — 프로젝트 #%d".formatted(budget.getProjectId());
            String body = "이번 기간 사용액이 예산의 %d%% 를 넘었습니다. 사용 %s USD / 예산 %s USD.".formatted(
                    alertRatioOf(budget).multiply(new BigDecimal("100")).intValue(),
                    usage.cost().stripTrailingZeros().toPlainString(),
                    budget.getAmountUsd().stripTrailingZeros().toPlainString());
            notificationService.notify(NotificationEventType.BUDGET_THRESHOLD, budget.getProjectId(), subject, body);
            fired++;
        }
        if (fired > 0) {
            log.info("[budget] 예산 경고 {}건을 보냈습니다", fired);
        }
        return fired;
    }
    /** 기간 창(정렬된 시작 ~ 다음 경계)과 그 키(같은 기간 재경고 방지용). */
    private static Window window(BudgetPeriod period, Instant now) {
        UsageInterval interval = UsageInterval.of(period);
        Instant from = interval.start(now);
        Instant to = interval.next(from);
        return new Window(from, to, interval.name().toLowerCase(Locale.ROOT) + ":" + from);
    }

    private static BigDecimal ratio(BigDecimal used, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return null;
        }
        return used.divide(amount, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal alertRatioOf(ProjectBudget budget) {
        return budget.getAlertRatio() == null ? DEFAULT_ALERT_RATIO : budget.getAlertRatio();
    }

    private record Window(Instant from, Instant to, String key) {
    }

    /** 더해 나가는 동안 쓰는 값 묶음(실행 수·비용·입출력 토큰). */
    private record Usage(long count, BigDecimal cost, long inputTokens, long outputTokens) {
        static Usage zero() {
            return new Usage(0L, BigDecimal.ZERO, 0L, 0L);
        }

        Usage plus(ExecutionUsagePoint point) {
            BigDecimal pointCost = point.costUsd() == null ? BigDecimal.ZERO : point.costUsd();
            long pointInput = point.inputTokens() == null ? 0L : point.inputTokens();
            long pointOutput = point.outputTokens() == null ? 0L : point.outputTokens();
            return new Usage(count + 1, cost.add(pointCost), inputTokens + pointInput, outputTokens + pointOutput);
        }
    }
}