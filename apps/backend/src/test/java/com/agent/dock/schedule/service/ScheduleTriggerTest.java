package com.agent.dock.schedule.service;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 스케줄러가 지난 예약을 워커 풀에 넘기는지 확인한다(실행 자체는 ScheduleService 책임). */
@ExtendWith(MockitoExtension.class)
class ScheduleTriggerTest {
    @Mock ScheduleService scheduleService;
    @InjectMocks ScheduleTrigger trigger;

    @Test
    void dispatchSendsEachDueIdToAWorker() {
        Instant now = Instant.now();
        when(scheduleService.dueIds(now)).thenReturn(List.of(1L, 2L));

        trigger.dispatch(now);

        verify(scheduleService, timeout(2000).times(2)).trigger(anyLong());
    }

    @Test
    void dispatchDoesNothingWhenNothingIsDue() {
        when(scheduleService.dueIds(any())).thenReturn(List.of());

        trigger.dispatch(Instant.now());

        verify(scheduleService, never()).trigger(anyLong());
    }
}
