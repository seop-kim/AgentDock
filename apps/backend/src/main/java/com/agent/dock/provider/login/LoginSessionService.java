package com.agent.dock.provider.login;

import com.agent.dock.common.BadRequestException;
import com.agent.dock.common.NotFoundException;
import com.agent.dock.provider.AiConnection;
import com.agent.dock.provider.AiConnectionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 웹 로그인 패널의 백엔드. Provider CLI 의 로그인 명령을 실행하고 출력을 세션에 쌓아 SSE 로 전달한다.
 * 자격증명은 CLI 가 자기 세션에 저장하며 이 앱은 다루지 않는다. 오래 걸리는 작업이므로 @Transactional 을 쓰지 않는다.
 */
@Service
@Slf4j
public class LoginSessionService {
    static final Duration IDLE_TIMEOUT = Duration.ofMinutes(5);

    private final AiConnectionRepository connectionRepository;
    private final LoginCommandRegistry commandRegistry;
    private final LoginProcessFactory processFactory;
    private final Clock clock;
    private final Map<String, LoginSession> sessions = new ConcurrentHashMap<>();
    private ScheduledExecutorService sweeper;

    @Autowired
    public LoginSessionService(AiConnectionRepository connectionRepository, LoginCommandRegistry commandRegistry,
                               LoginProcessFactory processFactory) {
        this(connectionRepository, commandRegistry, processFactory, Clock.systemUTC());
    }

    LoginSessionService(AiConnectionRepository connectionRepository, LoginCommandRegistry commandRegistry,
                        LoginProcessFactory processFactory, Clock clock) {
        this.connectionRepository = connectionRepository;
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

    /** 로그인 세션을 시작하고 sessionId 를 돌려준다. Connection 당 활성 세션은 하나이며 이미 있으면 그것을 돌려준다. */
    public String start(Long connectionId) {
        // provider 를 fetch join 으로 함께 읽는다(open-in-view=false 라 지연 로딩 불가)
        AiConnection connection = connectionRepository.findWithProvider(connectionId)
                .orElseThrow(() -> new NotFoundException("AiConnection %d not found".formatted(connectionId)));
        var providerKey = connection.getProvider().getKey();
        List<String> command = commandRegistry.find(providerKey)
                .orElseThrow(() -> new BadRequestException("이 Provider 는 로그인 창을 아직 지원하지 않습니다: " + providerKey))
                .command();

        for (LoginSession existing : sessions.values()) {
            if (existing.connectionId().equals(connectionId) && !existing.finished()) {
                return existing.id();
            }
        }

        LoginProcess process = processFactory.create();
        LoginSession session = new LoginSession(UUID.randomUUID().toString(), connectionId, process, clock);
        sessions.put(session.id(), session);
        process.onOutput(chunk -> session.publish(LoginEvent.output(chunk)));
        process.onExit(code -> session.publish(LoginEvent.exit(code)));
        try {
            process.start(command);
        } catch (IOException ex) {
            log.warn("login CLI start failed: {}", command.get(0), ex);
            session.publish(LoginEvent.output("CLI 실행 실패: " + command.get(0)
                    + " 를 찾을 수 없습니다 (CLAUDE_CODE_BIN 으로 경로 지정 가능)"));
            session.publish(LoginEvent.exit(-1));
        }
        return session.id();
    }

    public Runnable subscribe(String sessionId, Consumer<LoginEvent> subscriber) {
        return find(sessionId).subscribe(subscriber);
    }

    /** 프로세스 stdin 으로 한 줄을 전달한다(인증 코드 붙여넣기용). */
    public void input(String sessionId, String text) {
        LoginSession session = find(sessionId);
        if (session.finished()) {
            throw new BadRequestException("로그인 세션이 이미 종료되었습니다");
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
                session.publish(LoginEvent.output("\n[5분 동안 활동이 없어 로그인 세션을 종료합니다]\n"));
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
