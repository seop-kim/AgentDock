package com.agent.dock.budget.service;

import com.agent.dock.budget.domain.BudgetPeriod;
import com.agent.dock.budget.domain.ProjectBudget;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
/** 예산 설정·기간별 사용량 집계·80% 경고를 고정한다(값은 CLI 계측값만). */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock ProjectBudgetRepository budgetRepository;
    @Mock ProjectRepository projectRepository;
    @Mock TaskRepository taskRepository;
    @Mock ExecutionRepository executionRepository;
    @Mock NotificationService notificationService;
    @Mock EventPublisher changeEvents;
    private BudgetService service;

    @BeforeEach
    void setUp() {
        service = new BudgetService(budgetRepository, projectRepository, taskRepository, executionRepository,
                notificationService, changeEvents);
    }

    @Test
    void updateCreatesBudgetAndPublishesChange() {
        Project project = project(6L);
        when(projectRepository.findById(6L)).thenReturn(Optional.of(project));
        ProjectBudget[] saved = new ProjectBudget[1];
        when(budgetRepository.findByProjectId(6L)).thenReturn(Optional.empty()).thenAnswer(inv -> Optional.of(saved[0]));
        when(budgetRepository.save(any(ProjectBudget.class))).thenAnswer(inv -> {
            saved[0] = inv.getArgument(0);
            return inv.getArgument(0);
        });
        when(taskRepository.findIdsByProjectId(6L)).thenReturn(List.of());

        ProjectBudgetResponse response = service.update(6L, new UpdateBudgetRequest(new BigDecimal("12.5"), null, null));

        assertThat(response.configured()).isTrue();
        assertThat(response.amountUsd()).isEqualByComparingTo("12.50");
        assertThat(response.period()).isEqualTo(BudgetPeriod.MONTHLY);
        assertThat(response.alertRatio()).isEqualByComparingTo("0.80");
        verify(changeEvents).budgetChanged(6L);
    }
    @Test
    void budgetComputesRatioAndWarningFromPeriodUsage() {
        when(projectRepository.findById(6L)).thenReturn(Optional.of(project(6L)));
        when(budgetRepository.findByProjectId(6L)).thenReturn(Optional.of(budget(new BigDecimal("10.00"),
                BudgetPeriod.MONTHLY, new BigDecimal("0.80"))));
        when(taskRepository.findIdsByProjectId(6L)).thenReturn(List.of(10L));
        when(executionRepository.findUsagePointsByTaskIds(eq(List.of(10L)), any(), any()))
                .thenReturn(List.of(new ExecutionUsagePoint(Instant.now(), new BigDecimal("8.50"), 100, 40)));

        ProjectBudgetResponse response = service.budget(6L);

        assertThat(response.configured()).isTrue();
        assertThat(response.usedUsd()).isEqualByComparingTo("8.50");
        assertThat(response.ratio()).isEqualByComparingTo("0.8500");
        assertThat(response.warning()).isTrue();
    }

    @Test
    void budgetWithoutAConfigurationIsNotFatal() {
        when(projectRepository.findById(6L)).thenReturn(Optional.of(project(6L)));
        when(budgetRepository.findByProjectId(6L)).thenReturn(Optional.empty());
        when(taskRepository.findIdsByProjectId(6L)).thenReturn(List.of());

        ProjectBudgetResponse response = service.budget(6L);

        assertThat(response.configured()).isFalse();
        assertThat(response.amountUsd()).isNull();
        assertThat(response.warning()).isFalse();
        assertThat(response.usedUsd()).isEqualByComparingTo("0");
    }

    @Test
    void budgetForUnknownProjectIsNotFound() {
        when(projectRepository.findById(99999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.budget(99999L)).isInstanceOf(NotFoundException.class);
    }
    @Test
    void usageAggregatesPerDayBucketsAndTotals() {
        when(projectRepository.findById(6L)).thenReturn(Optional.of(project(6L)));
        when(taskRepository.findIdsByProjectId(6L)).thenReturn(List.of(10L));
        when(executionRepository.findUsagePointsByTaskIds(eq(List.of(10L)), any(), any())).thenReturn(List.of(
                new ExecutionUsagePoint(Instant.parse("2026-10-01T05:00:00Z"), new BigDecimal("0.10"), 100, 40),
                new ExecutionUsagePoint(Instant.parse("2026-10-02T05:00:00Z"), new BigDecimal("0.20"), 20, 10)));

        UsageResponse response = service.usage(6L, "2026-10-01T00:00:00Z", "2026-10-03T00:00:00Z", "DAY");

        assertThat(response.bucket()).isEqualTo("DAY");
        assertThat(response.executionCount()).isEqualTo(2);
        assertThat(response.totalCostUsd()).isEqualByComparingTo("0.30");
        assertThat(response.totalInputTokens()).isEqualTo(120);
        assertThat(response.totalOutputTokens()).isEqualTo(50);
        assertThat(response.buckets()).hasSize(2);
        assertThat(response.buckets().get(0).costUsd()).isEqualByComparingTo("0.10");
        assertThat(response.buckets().get(1).inputTokens()).isEqualTo(20);
    }

    @Test
    void usageRejectsBadRangeBucketAndUnknownProject() {
        assertThatThrownBy(() -> service.usage(null, "2026-10-03T00:00:00Z", "2026-10-01T00:00:00Z", "DAY"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.usage(null, null, null, "WEEKLYX"))
                .isInstanceOf(BadRequestException.class);
        when(projectRepository.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.usage(7L, null, null, "DAY")).isInstanceOf(NotFoundException.class);
    }

    private static Project project(Long id) {
        Project project = new Project();
        project.setId(id);
        return project;
    }

    private static ProjectBudget budget(BigDecimal amount, BudgetPeriod period, BigDecimal alertRatio) {
        ProjectBudget budget = new ProjectBudget();
        budget.setAmountUsd(amount);
        budget.setPeriod(period);
        budget.setAlertRatio(alertRatio);
        return budget;
    }
}
