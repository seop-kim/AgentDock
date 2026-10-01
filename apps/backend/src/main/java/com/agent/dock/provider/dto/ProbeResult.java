package com.agent.dock.provider.dto;

/**
 * 런타임 확인 결과.
 * ok=false 이면 detail 에 사용자가 읽을 수 있는 실패 이유가 들어간다(로그인 만료, 시간 초과 등).
 * cliMissing=true 는 실행 파일 자체가 없어서 실패한 경우로, 화면에서 설치를 안내한다.
 */
public record ProbeResult(boolean ok, String detail, boolean cliMissing) {

    public static ProbeResult success(String detail) {
        return new ProbeResult(true, detail, false);
    }

    public static ProbeResult failure(String detail) {
        return new ProbeResult(false, detail, false);
    }

    public static ProbeResult cliMissing(String detail) {
        return new ProbeResult(false, detail, true);
    }
}
