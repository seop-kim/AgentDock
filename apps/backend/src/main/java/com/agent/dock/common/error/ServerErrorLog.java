package com.agent.dock.common.error;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 서버 안에서 난 **내부 오류**(미처리 예외 = 5xx)를 최근 것만 메모리에 담아 둔다.
 *
 * <p>목적은 사람이 화면에서 원인을 보고 **에이전트에게 수정을 맡길지** 고르게 하는 것이다. 그래서 스택도 들고 있다.
 * 4xx(검증·없음·충돌)는 사용자 입력 문제라 담지 않는다 — 소음이 되고, 고칠 것도 없다.
 * 재시작하면 사라진다(영구 기록은 로그 파일이 맡는다).
 */
@Component
public class ServerErrorLog {

    /** 담아 두는 최대 건수. 넘치면 오래된 것부터 버린다. */
    private static final int MAX = 50;
    /** 화면에 보여 줄 스택 줄 수(전체 스택은 로그 파일에 있다). */
    private static final int STACK_LINES = 20;

    /** 한 건의 오류. 화면에 이대로 내려간다. */
    public record Entry(long id, Instant time, String method, String path, String exception,
                        String message, String stack) {
    }

    private final AtomicLong sequence = new AtomicLong();
    private final Deque<Entry> entries = new ArrayDeque<>();

    public Entry record(String method, String path, Throwable cause) {
        Entry entry = new Entry(sequence.incrementAndGet(), Instant.now(),
                method == null ? "" : method,
                path == null ? "" : path,
                cause.getClass().getName(),
                String.valueOf(cause.getMessage()),
                stackOf(cause));
        synchronized (entries) {
            entries.addFirst(entry);
            while (entries.size() > MAX) {
                entries.removeLast();
            }
        }
        return entry;
    }

    /** 최근 순(새 것이 먼저). */
    public List<Entry> recent() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    public Entry find(long id) {
        synchronized (entries) {
            return entries.stream().filter(entry -> entry.id() == id).findFirst().orElse(null);
        }
    }

    private static String stackOf(Throwable cause) {
        StringWriter writer = new StringWriter();
        cause.printStackTrace(new PrintWriter(writer));
        return String.join("\n", writer.toString().lines().limit(STACK_LINES).toList());
    }
}
