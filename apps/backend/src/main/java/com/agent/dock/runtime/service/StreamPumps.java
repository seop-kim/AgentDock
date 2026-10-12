package com.agent.dock.runtime.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 프로세스 출력(stdout/stderr)을 읽는 **전용** 스레드 풀.
 *
 * <p>`CompletableFuture.runAsync` 는 인자를 주지 않으면 **common ForkJoinPool** 을 빌려 쓴다. 그 풀은
 * (코어 수 - 1)개의 작은 풀이고 병렬 스트림 등 다른 작업과 공유되므로, 출력이 많은 실행에서는 펌프가 밀려
 * 실행 로그가 늦게 흐르고 화면도 그만큼 늦게 채워진다.
 *
 * <p>펌프는 서로를 기다리지 않으므로 캐시 풀이 맞다(무한 대기 없음). 스레드는 이름 있는 데몬이라
 * 로그에서 구분되고 앱 종료를 막지 않는다.
 */
public final class StreamPumps {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private static final ExecutorService POOL = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "agentdock-pump-" + SEQ.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private StreamPumps() {
    }

    public static ExecutorService pool() {
        return POOL;
    }
}
