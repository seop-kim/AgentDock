package com.agent.dock.execution;

import java.util.Locale;

public enum LogStream {
    STDOUT, STDERR, SYSTEM;

    /** Runtime 이 넘겨주는 스트림 이름(stdout/stderr/system)을 저장용 값으로 바꾼다. */
    public static LogStream fromName(String name) {
        return switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
            case "stderr" -> STDERR;
            case "system" -> SYSTEM;
            default -> STDOUT;
        };
    }
}
