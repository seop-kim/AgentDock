package com.agent.dock.schedule.service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 예약 실행 스케줄러. 주기적으로 "지금 실행할 때가 된" 예약을 찾아 <b>워커 풀</b>에 넘긴다 —
 * 실제 실행(명령·워크트리·CLI)이 스케줄 스레드를 막지 않게 워커가 맡는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleTrigger {
    private final ScheduleService scheduleService;

    private static final AtomicInteger WORKER_SEQ = new AtomicInteger();

    /** 워커 스레드(데몬). 여러 예약이 같은 시각에 몰려도 서로 기다리지 않게 캐시 풀을 쓴다. */
    private final ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "agentdock-schedule-" + WORKER_SEQ.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    void shutdownWorkers() {
        workers.shutdownNow();
    }

    /** 30초마다 훑는다(초기 20초 뒤 시작). cron 최소 단위가 분이라 늦어도 1분 안에 실행된다. */
    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT20S")
    public void tick() {
        dispatch(Instant.now());
    }

    /** 지금 실행할 예약 id 를 워커에 넘긴다(테스트에서 직접 부를 수 있게 package-private). */
    void dispatch(Instant now) {
        for (Long id : scheduleService.dueIds(now)) {
            workers.submit(() -> {
                try {
                    scheduleService.trigger(id);
                } catch (RuntimeException ex) {
                    log.warn("[schedule] 예약 실행 실패 (id {}): {}", id, ex.getMessage());
                }
            });
        }
    }
}
