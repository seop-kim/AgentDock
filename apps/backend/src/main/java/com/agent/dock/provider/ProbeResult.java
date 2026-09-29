package com.agent.dock.provider;

/** ok=false 이면 detail 에 사용자가 읽을 수 있는 실패 이유가 들어간다(로그인 만료 등). */
public record ProbeResult(boolean ok, String detail) {

    public static ProbeResult success(String detail) {
        return new ProbeResult(true, detail);
    }

    public static ProbeResult failure(String detail) {
        return new ProbeResult(false, detail);
    }
}
