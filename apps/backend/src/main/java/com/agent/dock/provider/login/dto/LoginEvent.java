package com.agent.dock.provider.login.dto;

/** 로그인 세션이 SSE 로 내보내는 이벤트. type 은 "output" 또는 "exit". */
public record LoginEvent(String type, String content, Integer exitCode) {

    public static LoginEvent output(String content) {
        return new LoginEvent("output", content, null);
    }

    public static LoginEvent exit(int exitCode) {
        return new LoginEvent("exit", null, exitCode);
    }

    /** JSON 직렬화 대상이 되지 않도록 bean getter 형식(isX)을 피한 이름을 쓴다. */
    public boolean exitEvent() {
        return "exit".equals(type);
    }
}
