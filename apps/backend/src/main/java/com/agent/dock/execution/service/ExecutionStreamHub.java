package com.agent.dock.execution.service;

import com.agent.dock.execution.domain.ExecutionLog;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.domain.LogStream;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실행 로그의 SSE 스트림을 관리한다.
 *
 * - 실행 중: 새 구독자에게 지금까지의 로그(메모리 버퍼)를 먼저 보내고 이어서 라이브로 보낸다.
 * - 끝난 실행: 저장된 로그를 재생하고 exit 이벤트를 보낸 뒤 닫는다(터미널 창을 나중에 열어도 전체가 보인다).
 * - 다중 구독: 하나의 emitter 가 여러 실행을 함께 받는다(터미널을 여러 개 열어도 연결은 하나).
 * - 로그는 화면(SSE)과 DB(execution_log)에 함께 남긴다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionStreamHub {
    /** 메모리 버퍼에 들고 있는 최대 줄 수. 넘치면 오래된 줄부터 버리고 그 사실을 로그에 남긴다. */
    private static final int BUFFER_LIMIT = 2000;
    /** 이미 끝난 실행을 다시 열 때 DB 에서 재생할 최대 줄 수. */
    private static final int REPLAY_LIMIT = 500;

    private final ExecutionLogRepository logRepository;
    private final ExecutionRepository executionRepository;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** 버퍼 한 줄(스트림 이름 + 내용). 실행 id 는 보낼 때 붙인다(버퍼가 실행별이라 중복 저장하지 않는다). */
    private record Line(String stream, String content) {
    }
    /**
     * 구독 하나. 하나의 emitter 가 여러 실행을 받을 수 있다(다중 구독).
     * keys 는 이 구독이 붙어 있는 실행들이라, 한 실행이 끝나도 다른 실행이 남으면 연결을 유지한다.
     */
    private static final class Subscriber {
        private final SseEmitter emitter;
        private final boolean multiplexed;
        private final Set<String> keys = ConcurrentHashMap.newKeySet();

        private Subscriber(SseEmitter emitter, boolean multiplexed) {
            this.emitter = emitter;
            this.multiplexed = multiplexed;
        }
    }

    private static final class Bucket {
        private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
        private final List<Line> lines = new CopyOnWriteArrayList<>();
        private int dropped;
    }

    /** 실행 시작. 이때부터 로그가 버퍼에 쌓인다. */
    public void open(Long executionId) {
        buckets.put(key(executionId), new Bucket());
    }
    /** 로그 한 줄: 버퍼에 쌓고, 구독자에게 보내고, DB 에 저장한다(단일·다중 구독자에게 함께 팬아웃). */
    public void line(Long executionId, LogStream stream, String content) {
        String key = key(executionId);
        Bucket bucket = buckets.get(key);
        Line line = new Line(streamName(stream), content);
        if (bucket != null) {
            synchronized (bucket.lines) {
                bucket.lines.add(line);
                if (bucket.lines.size() > BUFFER_LIMIT) {
                    bucket.lines.remove(0);
                    bucket.dropped++;
                }
            }
            for (Subscriber subscriber : bucket.subscribers) {
                if (!sendLine(subscriber, executionId, line)) {
                    release(subscriber);
                }
            }
        }
        persist(executionId, stream, content);
    }

    /** 우리 쪽 안내 문구(스텝 경계·위임 진행). */
    public void system(Long executionId, String content) {
        line(executionId, LogStream.SYSTEM, content.endsWith("\n") ? content : content + "\n");
    }
    /** 실행 종료: exit 을 보내고 구독자를 정리한다. 다중 구독은 그 실행만 떼고 나머지 실행 로그는 계속 받는다. */
    public void close(Long executionId, ExecutionStatus status, Integer exitCode) {
        String key = key(executionId);
        Bucket bucket = buckets.remove(key);
        if (bucket == null) {
            return;
        }
        Map<String, Object> exit = exitEvent(executionId, status, exitCode);
        for (Subscriber subscriber : bucket.subscribers) {
            sendExit(subscriber, exit);
            if (subscriber.multiplexed) {
                subscriber.keys.remove(key);
                if (subscriber.keys.isEmpty()) {
                    completeQuietly(subscriber.emitter);
                }
            } else {
                completeQuietly(subscriber.emitter);
            }
        }
    }
    /** 실행 하나를 구독한다(기존 단일 스트림). 끝난 실행이면 저장 로그를 재생하고 exit 을 보낸 뒤 닫는다. */
    public SseEmitter subscribe(Long executionId) {
        SseEmitter emitter = createEmitter();
        Subscriber subscriber = new Subscriber(emitter, false);
        String key = key(executionId);
        Bucket bucket = buckets.get(key);
        if (bucket != null) {
            if (!replayBuffer(bucket, subscriber, executionId)) {
                completeQuietly(emitter);
                return emitter;
            }
            register(bucket, subscriber, key);
            attachLifecycle(subscriber);
            return emitter;
        }
        replayStored(executionId, subscriber);
        sendExit(subscriber, exitOf(executionId));
        emitter.complete();
        return emitter;
    }
    /**
     * 여러 실행을 한 연결로 구독한다(터미널을 여러 개 열어도 SSE 는 하나). 각 실행별로 로그를 재생하고,
     * 살아 있는 실행은 그 bucket 에 등록해 라이브로 받는다. 끝난 실행은 재생 + 그 실행의 exit 만 보낸다.
     * 등록된 실행이 하나도 없으면(전부 끝났거나 모르는 id) 바로 닫고, 모르는 id 는 아무 이벤트도 보내지 않는다.
     */
    public SseEmitter subscribeMany(Collection<Long> ids) {
        SseEmitter emitter = createEmitter();
        Subscriber subscriber = new Subscriber(emitter, true);
        boolean registered = false;
        for (Long executionId : normalize(ids)) {
            String key = key(executionId);
            Bucket bucket = buckets.get(key);
            if (bucket != null) {
                if (!replayBuffer(bucket, subscriber, executionId)) {
                    completeQuietly(emitter);
                    return emitter;
                }
                register(bucket, subscriber, key);
                registered = true;
            } else {
                replayStored(executionId, subscriber);
                sendExit(subscriber, exitOf(executionId));
            }
        }
        if (registered) {
            attachLifecycle(subscriber);
        } else {
            emitter.complete();
        }
        return emitter;
    }
    /** 구독자 등록(버킷 + keys). 끊기면 keys 의 모든 버킷에서 뗀다. */
    private void register(Bucket bucket, Subscriber subscriber, String key) {
        bucket.subscribers.add(subscriber);
        subscriber.keys.add(key);
    }

    private void attachLifecycle(Subscriber subscriber) {
        subscriber.emitter.onCompletion(() -> release(subscriber));
        subscriber.emitter.onTimeout(() -> release(subscriber));
        subscriber.emitter.onError(error -> release(subscriber));
    }

    /** 이 구독을 붙어 있던 모든 실행에서 떼고 emitter 를 닫는다(버킷은 이미 사라졌을 수 있어 있으면 제거). */
    private void release(Subscriber subscriber) {
        for (String key : subscriber.keys) {
            Bucket bucket = buckets.get(key);
            if (bucket != null) {
                bucket.subscribers.remove(subscriber);
            }
        }
        subscriber.keys.clear();
        completeQuietly(subscriber.emitter);
    }

    /** 메모리 버퍼를 재생한다(넘친 로그 안내 포함). 중간에 실패하면 false(구독자가 떠났다). */
    private boolean replayBuffer(Bucket bucket, Subscriber subscriber, Long executionId) {
        List<Line> replay;
        int dropped;
        synchronized (bucket.lines) {
            replay = new ArrayList<>(bucket.lines);
            dropped = bucket.dropped;
        }
        boolean ok = true;
        if (dropped > 0) {
            ok = sendLine(subscriber, executionId, new Line("system",
                    "⎿ 이전 로그 %d줄은 화면에서 생략했습니다(로그 조회로 확인할 수 있습니다)\n".formatted(dropped)));
        }
        for (Line line : replay) {
            ok = sendLine(subscriber, executionId, line) && ok;
        }
        return ok;
    }
    /** 끝난(또는 시작 전) 실행의 저장 로그를 재생한다. */
    private void replayStored(Long executionId, Subscriber subscriber) {
        List<ExecutionLog> logs = logRepository.findByExecutionIdOrderByCreatedAtAsc(executionId);
        int from = Math.max(0, logs.size() - REPLAY_LIMIT);
        for (ExecutionLog stored : logs.subList(from, logs.size())) {
            sendLine(subscriber, executionId, new Line(streamName(stored.getStream()), stored.getContent()));
        }
    }

    /** 끝난(또는 모르는) 실행의 exit 이벤트. 모르는 id 면 null(그 id 의 exit 도 보내지 않는다). */
    private Map<String, Object> exitOf(Long executionId) {
        return executionRepository.findById(executionId)
                .map(execution -> exitEvent(executionId, execution.getStatus(), execution.getExitCode()))
                .orElse(null);
    }

    private boolean sendLine(Subscriber subscriber, Long executionId, Line line) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("executionId", executionId);
        payload.put("stream", line.stream());
        payload.put("content", line.content());
        return send(subscriber.emitter, payload, null);
    }

    private void sendExit(Subscriber subscriber, Map<String, Object> exit) {
        if (exit != null) {
            send(subscriber.emitter, exit, "exit");
        }
    }

    /** 이벤트 한 건 전송. name 이 있으면 named 이벤트(exit). 실패하면 false(호출부가 정리한다). */
    private boolean send(SseEmitter emitter, Map<String, ?> payload, String name) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event();
            if (name != null) {
                event = event.name(name);
            }
            emitter.send(event.data(payload));
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }
    private Map<String, Object> exitEvent(Long executionId, ExecutionStatus status, Integer exitCode) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("executionId", executionId);
        event.put("status", status == null ? ExecutionStatus.FAILED.name() : status.name());
        event.put("exitCode", exitCode == null ? -1 : exitCode);
        return event;
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // 이미 닫힌 emitter — 더 할 일이 없다.
        }
    }

    private static List<Long> normalize(Collection<Long> ids) {
        Set<Long> unique = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    unique.add(id);
                }
            }
        }
        return List.copyOf(unique);
    }

    private static String key(Long executionId) {
        return String.valueOf(executionId);
    }

    private void persist(Long executionId, LogStream stream, String content) {
        try {
            ExecutionLog entry = new ExecutionLog();
            entry.setExecution(executionRepository.getReferenceById(executionId));
            entry.setStream(stream);
            entry.setContent(content);
            logRepository.save(entry);
        } catch (Exception ex) {
            log.warn("failed to persist log for execution {}: {}", executionId, ex.getMessage());
        }
    }

    private static String streamName(LogStream stream) {
        return stream.name().toLowerCase(Locale.ROOT);
    }

    /** 구독자 emitter 생성 — 테스트가 녹음 emitter 로 바꿔 끼울 수 있게 열어 둔다. */
    SseEmitter createEmitter() {
        return new SseEmitter(0L);
    }
}
