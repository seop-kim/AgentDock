package com.agent.dock.execution.service;

import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionLog;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.domain.LogStream;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 실행 로그 다중 스트림 허브: 하나의 emitter 가 여러 실행 로그를 함께 받고(팬아웃), 한 실행이 끝나도 다른 실행
 * 로그는 계속 흐르며, 구독이 끊기면 붙어 있던 모든 실행에서 정리된다. 단일 구독은 예전 payload 를 유지한다.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionStreamHubTest {

    @Mock ExecutionLogRepository logRepository;
    @Mock ExecutionRepository executionRepository;

    private RecordingHub newHub() {
        return new RecordingHub(logRepository, executionRepository);
    }
    @Test
    void singleSubscriberReplaysTheBufferAndThenStreamsLive() {
        RecordingHub hub = newHub();
        hub.open(1L);
        hub.line(1L, LogStream.STDOUT, "first\n");

        hub.subscribe(1L);
        RecordingEmitter emitter = hub.last();
        assertThat(emitter.lines()).anyMatch(line -> line.get("executionId").equals(1L)
                && line.get("content").equals("first\n"));

        hub.line(1L, LogStream.STDOUT, "second\n");
        assertThat(emitter.lines()).anyMatch(line -> line.get("content").equals("second\n"));
    }

    @Test
    void multiplexedSubscriberReceivesLogsFromEveryExecution() {
        RecordingHub hub = newHub();
        hub.open(1L);
        hub.open(2L);
        hub.subscribeMany(List.of(1L, 2L));
        RecordingEmitter emitter = hub.last();

        hub.line(1L, LogStream.STDOUT, "a\n");
        hub.line(2L, LogStream.STDOUT, "b\n");

        assertThat(emitter.lines()).anyMatch(line -> line.get("executionId").equals(1L)
                && line.get("content").equals("a\n"));
        assertThat(emitter.lines()).anyMatch(line -> line.get("executionId").equals(2L)
                && line.get("content").equals("b\n"));
    }
    @Test
    void duplicateIdsAreSubscribedOnce() {
        RecordingHub hub = newHub();
        hub.open(1L);
        hub.subscribeMany(List.of(1L, 1L, 1L));
        RecordingEmitter emitter = hub.last();

        hub.line(1L, LogStream.STDOUT, "once\n");

        assertThat(emitter.lines()).hasSize(1);
    }

    @Test
    void closingOneExecutionKeepsTheMultiplexedConnectionForTheRest() {
        RecordingHub hub = newHub();
        hub.open(1L);
        hub.open(2L);
        hub.subscribeMany(List.of(1L, 2L));
        RecordingEmitter emitter = hub.last();

        hub.close(1L, ExecutionStatus.SUCCEEDED, 0);

        assertThat(emitter.exits()).anyMatch(exit -> exit.get("executionId").equals(1L));
        assertThat(emitter.completed()).isFalse();

        hub.line(2L, LogStream.STDOUT, "still\n");
        assertThat(emitter.lines()).anyMatch(line -> line.get("content").equals("still\n"));
    }

    @Test
    void closingTheLastExecutionCompletesTheMultiplexedConnection() {
        RecordingHub hub = newHub();
        hub.open(1L);
        hub.subscribeMany(List.of(1L));
        RecordingEmitter emitter = hub.last();

        hub.close(1L, ExecutionStatus.SUCCEEDED, 0);

        assertThat(emitter.exits()).anyMatch(exit -> exit.get("executionId").equals(1L));
        assertThat(emitter.completed()).isTrue();
    }
    @Test
    void finishedExecutionReplaysStoredLogsAndSendsItsExitThenCloses() {
        RecordingHub hub = newHub();
        ExecutionLog stored = new ExecutionLog();
        stored.setStream(LogStream.STDOUT);
        stored.setContent("stored\n");
        when(logRepository.findByExecutionIdOrderByCreatedAtAsc(9L)).thenReturn(List.of(stored));
        Execution execution = new Execution();
        execution.setStatus(ExecutionStatus.SUCCEEDED);
        execution.setExitCode(0);
        when(executionRepository.findById(9L)).thenReturn(Optional.of(execution));

        hub.subscribeMany(List.of(9L));
        RecordingEmitter emitter = hub.last();

        assertThat(emitter.lines()).anyMatch(line -> line.get("executionId").equals(9L)
                && line.get("content").equals("stored\n"));
        assertThat(emitter.exits()).anyMatch(exit -> exit.get("executionId").equals(9L)
                && exit.get("status").equals("SUCCEEDED"));
        assertThat(emitter.completed()).isTrue();
    }

    @Test
    void unknownExecutionIdProducesNoEvents() {
        RecordingHub hub = newHub();
        when(logRepository.findByExecutionIdOrderByCreatedAtAsc(404L)).thenReturn(List.of());
        when(executionRepository.findById(404L)).thenReturn(Optional.empty());

        hub.subscribeMany(List.of(404L));
        RecordingEmitter emitter = hub.last();

        assertThat(emitter.maps()).isEmpty();
        assertThat(emitter.completed()).isTrue();
    }
    @Test
    void aFailedSendDropsTheSubscriberFromEveryBucket() {
        RecordingHub hub = newHub();
        hub.open(1L);
        hub.open(2L);
        hub.subscribeMany(List.of(1L, 2L));
        RecordingEmitter emitter = hub.last();
        emitter.failSends();

        hub.line(1L, LogStream.STDOUT, "boom\n");
        int sends = emitter.sends();
        hub.line(2L, LogStream.STDOUT, "after\n");

        assertThat(emitter.sends()).isEqualTo(sends);
    }

    /** 테스트용 허브 — 만든 emitter 를 녹음용으로 바꿔 끼운다. */
    private static final class RecordingHub extends ExecutionStreamHub {
        private final List<RecordingEmitter> created = new ArrayList<>();

        RecordingHub(ExecutionLogRepository logRepository, ExecutionRepository executionRepository) {
            super(logRepository, executionRepository);
        }

        @Override
        SseEmitter createEmitter() {
            RecordingEmitter emitter = new RecordingEmitter();
            created.add(emitter);
            return emitter;
        }

        RecordingEmitter last() {
            return created.get(created.size() - 1);
        }
    }
    /** 보낸 이벤트(data)를 모으고, 완료 여부와 전송 실패 주입을 함께 본다. */
    private static final class RecordingEmitter extends SseEmitter {
        private final List<Object> data = new ArrayList<>();
        private int sends;
        private int failAfter = Integer.MAX_VALUE;
        private boolean completed;

        RecordingEmitter() {
            super(0L);
        }

        void failSends() {
            failAfter = 0;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (sends >= failAfter) {
                throw new IOException("subscriber is gone");
            }
            sends++;
            for (DataWithMediaType item : builder.build()) {
                data.add(item.getData());
            }
        }

        @Override
        public void complete() {
            completed = true;
            super.complete();
        }

        int sends() {
            return sends;
        }

        boolean completed() {
            return completed;
        }

        List<Map<String, Object>> maps() {
            List<Map<String, Object>> maps = new ArrayList<>();
            for (Object item : data) {
                if (item instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cast = (Map<String, Object>) map;
                    maps.add(cast);
                }
            }
            return maps;
        }

        List<Map<String, Object>> lines() {
            return maps().stream().filter(map -> map.containsKey("content")).toList();
        }

        List<Map<String, Object>> exits() {
            return maps().stream().filter(map -> map.containsKey("status")).toList();
        }
    }
}
