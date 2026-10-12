package com.agent.dock.event.service;

import com.agent.dock.event.dto.DataChangedEvent;
import com.agent.dock.event.dto.EventTypes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 전역 이벤트 허브: 구독하면 hello 를 한 번 받고, 모든 구독자가 필터 없이 같은 이벤트를 받고(projectId 포함),
 * 끊긴 구독자는 그 자리에서 버려진다. 하트비트는 이벤트가 아니라 주석 줄이다.
 */
class EventStreamServiceTest {

    private final RecordingEventStream service = new RecordingEventStream();

    @Test
    void sendsHelloOnSubscribe() {
        service.subscribe();

        assertThat(service.subscriberCount()).isEqualTo(1);
        assertThat(service.emitter(0).data()).contains(Map.of("type", "hello"));
    }

    @Test
    void broadcastsEveryEventToEverySubscriberWithoutFiltering() {
        service.subscribe();
        service.subscribe();
        assertThat(service.subscriberCount()).isEqualTo(2);

        service.publish(new DataChangedEvent(EventTypes.EXECUTION_CHANGED, 6L, 42L, 7L));
        service.publish(new DataChangedEvent(EventTypes.TASK_CHANGED, 11L, null, 3L));
        service.publish(new DataChangedEvent(EventTypes.RUNTIME_CHANGED, null, null, null));

        // 필터가 없다(전역) — 두 구독자가 같은 3건을 같은 순서로 받는다.
        for (int index = 0; index < 2; index++) {
            assertThat(service.emitter(index).events()).containsExactly(
                    new DataChangedEvent(EventTypes.EXECUTION_CHANGED, 6L, 42L, 7L),
                    new DataChangedEvent(EventTypes.TASK_CHANGED, 11L, null, 3L),
                    new DataChangedEvent(EventTypes.RUNTIME_CHANGED, null, null, null));
        }
    }

    @Test
    void carriesProjectIdOfTheChangedSlice() {
        service.subscribe();

        service.publish(new DataChangedEvent(EventTypes.GROUP_CHANGED, 6L, null, null));

        DataChangedEvent received = service.emitter(0).events().get(0);
        assertThat(received.type()).isEqualTo("group.changed");
        assertThat(received.projectId()).isEqualTo(6L);
        assertThat(received.executionId()).isNull();
        assertThat(received.taskId()).isNull();
    }

    @Test
    void dropsABrokenSubscriberAndKeepsTheRest() {
        service.subscribe();
        service.nextIsBroken();
        service.subscribe();
        assertThat(service.subscriberCount()).isEqualTo(2);

        service.publish(new DataChangedEvent(EventTypes.AGENT_CHANGED, 6L, null, null));

        assertThat(service.subscriberCount()).isEqualTo(1);
        assertThat(service.emitter(0).events()).hasSize(1);
    }

    @Test
    void heartbeatSendsACommentAndNotAnEvent() {
        service.subscribe();
        RecordingEmitter subscriber = service.emitter(0);
        int afterHello = subscriber.sentCount();

        service.heartbeatTick();

        // 보내긴 했지만(연결 유지) 주석 줄이라 이벤트(data)는 늘지 않는다.
        assertThat(subscriber.sentCount()).isEqualTo(afterHello + 1);
        assertThat(subscriber.events()).isEmpty();
    }

    /** 구독자 emitter 를 갈아 끼워 무엇을 보냈는지 확인할 수 있게 한 허브(테스트용). */
    private static final class RecordingEventStream extends EventStreamService {
        private final List<RecordingEmitter> created = new ArrayList<>();
        private boolean nextIsBroken;

        void nextIsBroken() {
            nextIsBroken = true;
        }

        @Override
        SseEmitter createEmitter() {
            if (nextIsBroken) {
                nextIsBroken = false;
                return new BrokenEmitter();
            }
            RecordingEmitter emitter = new RecordingEmitter();
            created.add(emitter);
            return emitter;
        }

        RecordingEmitter emitter(int index) {
            return created.get(index);
        }
    }

    /** 보낸 것을 모으는 구독자. */
    private static final class RecordingEmitter extends SseEmitter {
        private final List<Object> data = new ArrayList<>();
        private int sends;

        RecordingEmitter() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sends++;
            for (DataWithMediaType item : builder.build()) {
                data.add(item.getData());
            }
        }

        List<Object> data() {
            return List.copyOf(data);
        }

        List<DataChangedEvent> events() {
            return data.stream().filter(DataChangedEvent.class::isInstance)
                    .map(DataChangedEvent.class::cast).toList();
        }

        int sentCount() {
            return sends;
        }
    }

    /** 끊긴 구독자(hello 는 받고, 그 뒤부터 IOException). */
    private static final class BrokenEmitter extends SseEmitter {
        private int sends;

        BrokenEmitter() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sends++;
            if (sends > 1) {
                throw new IOException("subscriber is gone");
            }
        }
    }
}
