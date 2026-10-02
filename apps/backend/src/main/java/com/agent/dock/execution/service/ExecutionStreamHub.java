package com.agent.dock.execution.service;

import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionLog;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.domain.LogStream;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실행 로그의 SSE 스트림을 관리한다.
 * - 실행 중: 새 구독자에게 지금까지의 로그(메모리 버퍼)를 먼저 보내고 이어서 라이브로 보낸다.
 * - 끝난 실행: 저장된 로그를 재생하고 exit 이벤트를 보낸 뒤 닫는다(터미널 창을 나중에 열어도 전체가 보인다).
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

    private static final class Bucket {
        private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        private final List<Map<String, String>> lines = new CopyOnWriteArrayList<>();
        private int dropped;
    }

    /** 실행 시작. 이때부터 로그가 버퍼에 쌓인다. */
    public void open(Long executionId) {
        buckets.put(String.valueOf(executionId), new Bucket());
    }

    /** 로그 한 줄: 버퍼에 쌓고, 화면에 보내고, DB 에 저장한다. */
    public void line(Long executionId, LogStream stream, String content) {
        Bucket bucket = buckets.get(String.valueOf(executionId));
        Map<String, String> event = Map.of("stream", streamName(stream), "content", content);
        if (bucket != null) {
            synchronized (bucket.lines) {
                bucket.lines.add(event);
                if (bucket.lines.size() > BUFFER_LIMIT) {
                    bucket.lines.remove(0);
                    bucket.dropped++;
                }
            }
            for (SseEmitter emitter : bucket.emitters) {
                sendOne(emitter, event, bucket.emitters);
            }
        }
        persist(executionId, stream, content);
    }

    /** 우리 쪽 안내 문구(스텝 경계·위임 진행). */
    public void system(Long executionId, String content) {
        line(executionId, LogStream.SYSTEM, content.endsWith("\n") ? content : content + "\n");
    }

    /** 실행 종료: exit 이벤트를 보내고 구독자와 버퍼를 정리한다. */
    public void close(Long executionId, ExecutionStatus status, Integer exitCode) {
        Bucket bucket = buckets.remove(String.valueOf(executionId));
        if (bucket == null) {
            return;
        }
        Map<String, Object> exit = exitEvent(status, exitCode);
        for (SseEmitter emitter : bucket.emitters) {
            try {
                emitter.send(SseEmitter.event().name("exit").data(exit));
                emitter.complete();
            } catch (IOException ex) {
                // 구독자가 이미 떠났으면 **오류를 컨테이너로 넘기지 않고** 조용히 닫는다 —
                // completeWithError 는 그 예외를 요청 스레드로 다시 던져 ERROR 로그를 쌓는다(전역 이벤트 허브에서 실측).
                try {
                    emitter.complete();
                } catch (RuntimeException ignored) {
                    // 이미 닫힌 emitter — 더 할 일이 없다.
                }
            }
        }
    }

    public SseEmitter subscribe(Long executionId) {
        SseEmitter emitter = new SseEmitter(0L);
        Bucket bucket = buckets.get(String.valueOf(executionId));
        if (bucket != null) {
            List<Map<String, String>> replay;
            int dropped;
            synchronized (bucket.lines) {
                replay = new ArrayList<>(bucket.lines);
                dropped = bucket.dropped;
            }
            if (dropped > 0) {
                sendOne(emitter, Map.of("stream", "system",
                        "content", "⎿ 이전 로그 %d줄은 화면에서 생략했습니다(로그 조회로 확인할 수 있습니다)\n".formatted(dropped)), null);
            }
            for (Map<String, String> line : replay) {
                sendOne(emitter, line, null);
            }
            bucket.emitters.add(emitter);
            emitter.onCompletion(() -> bucket.emitters.remove(emitter));
            emitter.onTimeout(() -> bucket.emitters.remove(emitter));
            return emitter;
        }

        // 이미 끝났거나 시작 전: 저장된 로그를 재생하고 종료 상태를 알린 뒤 닫는다.
        List<ExecutionLog> logs = logRepository.findByExecutionIdOrderByCreatedAtAsc(executionId);
        int from = Math.max(0, logs.size() - REPLAY_LIMIT);
        for (ExecutionLog stored : logs.subList(from, logs.size())) {
            sendOne(emitter, Map.of("stream", streamName(stored.getStream()), "content", stored.getContent()), null);
        }
        executionRepository.findById(executionId).ifPresent(execution -> {
            try {
                emitter.send(SseEmitter.event().name("exit").data(exitEvent(execution.getStatus(), execution.getExitCode())));
            } catch (IOException ignored) {
                // 구독자가 이미 떠났으면 그냥 닫는다.
            }
        });
        emitter.complete();
        return emitter;
    }

    private Map<String, Object> exitEvent(ExecutionStatus status, Integer exitCode) {
        return Map.of("status", status == null ? ExecutionStatus.FAILED.name() : status.name(),
                "exitCode", exitCode == null ? -1 : exitCode);
    }

    private void sendOne(SseEmitter emitter, Map<String, ?> payload, List<SseEmitter> cleanup) {
        try {
            emitter.send(SseEmitter.event().data(payload));
        } catch (IOException ex) {
            if (cleanup != null) {
                cleanup.remove(emitter);
            }
        }
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
}
