package com.agent.dock.provider.login.service;

import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.provider.domain.AiProvider;
import com.agent.dock.provider.login.domain.SessionKind;
import com.agent.dock.provider.login.dto.LoginEvent;
import com.agent.dock.provider.login.interfaces.LoginProcess;
import com.agent.dock.provider.login.util.CommandShell;
import com.agent.dock.provider.repository.AiProviderRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 런타임 CLI 명령 세션(로그인/설치)의 백엔드. 명령을 실행하고 출력을 세션에 쌓아 SSE 로 전달한다.
 * 자격증명은 CLI 가 자기 세션에 저장하며 이 앱은 다루지 않는다. 오래 걸리는 작업이므로 @Transactional 을 쓰지 않는다.
 *
 * 설치 계획(capabilities): installRequire(예: npm) → installPrerequisite(예: nvm install lts) → install(예: npm install -g ...).
 * 설치 단계는 셸로 감싸 실행한다(`cmd.exe /c`). npm 처럼 .cmd/.ps1 로만 존재하는 실행 파일과 `&&` 같은 복합 명령을 그대로 쓸 수 있다.
 */
@Service
@Slf4j
public class LoginSessionService {
    static final Duration IDLE_TIMEOUT = Duration.ofMinutes(5);

    private final AiProviderRepository providerRepository;
    private final LoginCommandRegistry commandRegistry;
    private final LoginProcessFactory processFactory;
    private final Clock clock;
    private final Map<String, LoginSession> sessions = new ConcurrentHashMap<>();
    private ScheduledExecutorService sweeper;

    @Autowired
    public LoginSessionService(AiProviderRepository providerRepository, LoginCommandRegistry commandRegistry,
                               LoginProcessFactory processFactory) {
        this(providerRepository, commandRegistry, processFactory, Clock.systemUTC());
    }

    LoginSessionService(AiProviderRepository providerRepository, LoginCommandRegistry commandRegistry,
                        LoginProcessFactory processFactory, Clock clock) {
        this.providerRepository = providerRepository;
        this.commandRegistry = commandRegistry;
        this.processFactory = processFactory;
        this.clock = clock;
    }

    @PostConstruct
    void startSweeper() {
        sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "login-session-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        sweeper.scheduleWithFixedDelay(this::sweepIdle, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        if (sweeper != null) {
            sweeper.shutdownNow();
        }
        sessions.values().forEach(session -> session.process().close());
    }

    public String start(Long providerId) {
        return start(providerId, SessionKind.LOGIN);
    }

    /** 런타임 + 종류당 활성 세션은 하나이며, 이미 있으면 그 세션을 돌려준다. */
    public String start(Long providerId, SessionKind kind) {
        AiProvider provider = providerRepository.findByIdAndDeletedAtIsNull(providerId)
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(providerId)));
        List<List<String>> steps = switch (kind) {
            case LOGIN -> List.of(CommandShell.wrap(String.join(" ",
                    commandRegistry.find(provider.getKey())
                            .orElseThrow(() -> new BadRequestException(
                                    "이 런타임은 로그인 창을 아직 지원하지 않습니다: " + provider.getKey()))
                            .command())));
            case INSTALL -> installSteps(provider);
        };

        for (LoginSession existing : sessions.values()) {
            if (existing.providerId().equals(providerId) && existing.kind() == kind && !existing.finished()) {
                return existing.id();
            }
        }

        LoginProcess process = processFactory.create();
        LoginSession session = new LoginSession(UUID.randomUUID().toString(), providerId, kind, process, clock);
        sessions.put(session.id(), session);
        process.onOutput(chunk -> session.publish(LoginEvent.output(chunk)));
        process.onExit(code -> session.publish(LoginEvent.exit(code)));
        try {
            process.start(steps);
        } catch (IOException ex) {
            log.warn("{} start failed: {}", kind, steps, ex);
            session.publish(LoginEvent.output("CLI 실행 실패: " + ex.getMessage()
                    + " — 에이전트 설정에서 설치 명령을 확인하세요"));
            session.publish(LoginEvent.exit(-1));
        }
        return session.id();
    }

    /**
     * 설치 단계. 필수 실행 파일(installRequire)이 없으면 선행 도구 설치 단계를 먼저 붙인다.
     * 각 단계는 셸로 감싸므로 복합 명령/`&&`/`.cmd` 실행이 가능하다.
     */
    private List<List<String>> installSteps(AiProvider provider) {
        Map<String, Object> capabilities = provider.getCapabilities();
        List<String> install = stringList(capabilities, "install");
        if (install.isEmpty()) {
            throw new BadRequestException("설치 명령이 등록되지 않았습니다. 에이전트 설정에서 입력하세요: " + provider.getKey());
        }

        List<List<String>> steps = new ArrayList<>();
        String require = stringValue(capabilities, "installRequire");
        if (require != null && !require.isBlank() && !canRun(require)) {
            List<String> prerequisite = stringList(capabilities, "installPrerequisite");
            if (prerequisite.isEmpty()) {
                throw new BadRequestException("필수 도구가 없습니다: " + require
                        + " — 에이전트 설정에 '필수 도구 설치 명령'을 추가하세요");
            }
            steps.add(CommandShell.wrap("echo [prerequisite] required tool is missing: " + require));
            prerequisite.forEach(command -> steps.add(CommandShell.wrap(command)));
            steps.add(CommandShell.wrap("echo [prerequisite] done - starting CLI install"));
        }
        install.forEach(command -> steps.add(CommandShell.wrap(command)));
        return steps;
    }

    /** 필수 실행 파일이 실행 가능한지 확인한다(셸을 통해 --version). */
    private boolean canRun(String executable) {
        try {
            Process process = new ProcessBuilder(CommandShell.wrap(executable + " --version"))
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private List<String> stringList(Map<String, Object> capabilities, String key) {
        Object value = capabilities == null ? null : capabilities.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(item -> !item.isEmpty()).toList();
        }
        return Arrays.stream(String.valueOf(value).split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String stringValue(Map<String, Object> capabilities, String key) {
        Object value = capabilities == null ? null : capabilities.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public Runnable subscribe(String sessionId, Consumer<LoginEvent> subscriber) {
        return find(sessionId).subscribe(subscriber);
    }

    /** 프로세스 stdin 으로 한 줄을 전달한다(인증 코드 붙여넣기, 설치 중 입력). */
    public void input(String sessionId, String text) {
        LoginSession session = find(sessionId);
        if (session.finished()) {
            throw new BadRequestException("세션이 이미 종료되었습니다");
        }
        try {
            session.process().write(text + "\n");
            session.touch();
        } catch (IOException ex) {
            throw new BadRequestException("입력을 전달하지 못했습니다: " + ex.getMessage());
        }
    }

    public void stop(String sessionId) {
        find(sessionId).process().close();
    }

    /** 유휴 시간이 지난 세션을 정리한다. 실행 중이면 종료시키고, 끝난 세션은 목록에서 지운다. */
    void sweepIdle() {
        Instant threshold = clock.instant().minus(IDLE_TIMEOUT);
        for (LoginSession session : List.copyOf(sessions.values())) {
            if (session.lastActivity().isAfter(threshold)) {
                continue;
            }
            if (session.finished()) {
                sessions.remove(session.id());
            } else {
                session.publish(LoginEvent.output("\n[5분 동안 활동이 없어 세션을 종료합니다]\n"));
                session.process().close();
            }
        }
    }

    private LoginSession find(String sessionId) {
        LoginSession session = sessions.get(sessionId);
        if (session == null) {
            throw new NotFoundException("Login session %s not found".formatted(sessionId));
        }
        return session;
    }
}
