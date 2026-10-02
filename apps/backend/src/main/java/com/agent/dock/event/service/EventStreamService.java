package com.agent.dock.event.service;

import com.agent.dock.event.dto.DataChangedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 전역 데이터 변경 이벤트(SSE) 허브. 실행 로그 허브({@code execution/service/ExecutionStreamHub})와 같은 수법이다 —
 * 구독자를 목록으로 들고, 모두에게 보내고, IOException 이 난 구독자는 그 자리에서 버린다.
 *
 * <p>실행 로그 스트림과 다른 점은 **필터가 없다는 것**과 **끝나지 않는다는 것**이다: 연결 하나가 전역이고,
 * 이벤트는 "무엇이 바뀌었는지"만 나른다(클라이언트가 바뀐 조각만 REST 로 다시 읽는다).
 *
 * <ul>
 *   <li>구독하면 {@code hello} 이벤트를 한 번 보낸다(연결 확인용).</li>
 *   <li>유휴 연결이 프록시에 끊기지 않게 25초마다 주석 한 줄(`: keep-alive`)을 보낸다(이벤트가 아니다 — data 가 없다).</li>
 *   <li>데이터 이벤트는 **이름 없는 기본 이벤트**(`message`)로 보내므로 `EventSource.onmessage` 하나로 다 받는다.</li>
 *   <li>타임아웃은 두지 않는다(0L). 끊긴 연결은 onCompletion/onTimeout/onError 와 전송 실패로 정리한다.</li>
 * </ul>
 */
@Component
@Slf4j
public class EventStreamService {
    /** 하트비트 주기(초). 프록시가 유휴 연결을 끊지 않을 만큼 짧게, 트래픽은 무시할 만큼 길게. */
    static final long HEARTBEAT_SECONDS = 25;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    /** SseEmitter 는 동시 send 를 보장하지 않는다 — 하트비트 스레드와 서비스 스레드가 겹치지 않게 한 잠금으로 직렬화한다. */
    private final Object sendLock = new Object();
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "event-stream-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    void startHeartbeat() {
        heartbeats.scheduleAtFixedRate(this::heartbeatTick, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopHeartbeat() {
        heartbeats.shutdownNow();
    }

    /** 전역 스트림 구독. hello 한 번 보낸 뒤에는 데이터 이벤트와 하트비트만 흐른다. */
    public SseEmitter subscribe() {
        SseEmitter emitter = createEmitter();
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(exception -> emitters.remove(emitter));
        emitters.add(emitter);
        send(emitter, SseEmitter.event().name("hello").data(Map.of("type", "hello")));
        return emitter;
    }

    /** 구독자 하나(타임아웃 없음). 테스트가 보낸 것을 확인하려고 갈아 끼울 수 있게 메서드로 둔다. */
    SseEmitter createEmitter() {
        return new SseEmitter(0L);
    }

    /** 데이터가 바뀌었음을 모든 구독자에게 알린다(구독자가 없으면 아무 일도 하지 않는다 — 필터 없음 = 전역). */
    public void publish(DataChangedEvent event) {
        if (event == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().data(event));
        }
    }

    /** 하트비트 한 번(테스트에서 직접 부를 수 있게 package-private). 주석 줄이라 클라이언트 이벤트가 되지 않는다. */
    void heartbeatTick() {
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().comment("keep-alive"));
        }
    }

    /** 지금 붙어 있는 구독자 수(테스트·진단용). */
    public int subscriberCount() {
        return emitters.size();
    }

    /** 한 구독자에게 보낸다. 끊긴 구독자(IOException/완료된 emitter)는 목록에서 빼고 버린다. */
    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            synchronized (sendLock) {
                emitter.send(event);
            }
        } catch (IOException | IllegalStateException ex) {
            drop(emitter);
        }
    }

    private void drop(SseEmitter emitter) {
        emitters.remove(emitter);
        try {
            emitter.completeWithError(new IOException("event stream subscriber is gone"));
        } catch (RuntimeException ex) {
            log.debug("failed to complete a dead event stream subscriber: {}", ex.getMessage());
        }
    }
}
