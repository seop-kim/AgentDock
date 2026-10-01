package com.agent.dock.provider.login.service;

import com.agent.dock.provider.login.domain.SessionKind;
import com.agent.dock.provider.login.dto.LoginEvent;
import com.agent.dock.provider.login.interfaces.LoginProcess;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.List;

/**
 * 런타임 CLI 명령(로그인/설치) 한 번의 수명. 이벤트를 모두 쌓아 두고, 구독자에게 지금까지의 이벤트를 재생한 뒤
 * 실시간으로 전달한다(구독이 프로세스 시작보다 늦어도 인증 URL/설치 로그 출력을 놓치지 않기 위함).
 */
class LoginSession {
    private final String id;
    private final Long providerId;
    private final SessionKind kind;
    private final LoginProcess process;
    private final Clock clock;
    private final List<LoginEvent> events = new ArrayList<>();
    private final List<Consumer<LoginEvent>> subscribers = new ArrayList<>();
    private boolean finished;
    private Instant lastActivity;

    LoginSession(String id, Long providerId, SessionKind kind, LoginProcess process, Clock clock) {
        this.id = id;
        this.providerId = providerId;
        this.kind = kind;
        this.process = process;
        this.clock = clock;
        this.lastActivity = clock.instant();
    }

    String id() {
        return id;
    }

    Long providerId() {
        return providerId;
    }

    SessionKind kind() {
        return kind;
    }

    LoginProcess process() {
        return process;
    }

    synchronized boolean finished() {
        return finished;
    }

    synchronized Instant lastActivity() {
        return lastActivity;
    }

    synchronized void touch() {
        lastActivity = clock.instant();
    }

    synchronized void publish(LoginEvent event) {
        events.add(event);
        lastActivity = clock.instant();
        if (event.exitEvent()) {
            finished = true;
        }
        for (Consumer<LoginEvent> subscriber : List.copyOf(subscribers)) {
            subscriber.accept(event);
        }
    }

    synchronized Runnable subscribe(Consumer<LoginEvent> subscriber) {
        events.forEach(subscriber);
        if (!finished) {
            subscribers.add(subscriber);
        }
        return () -> {
            synchronized (LoginSession.this) {
                subscribers.remove(subscriber);
            }
        };
    }
}
