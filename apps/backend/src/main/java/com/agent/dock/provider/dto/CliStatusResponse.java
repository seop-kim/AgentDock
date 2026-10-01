package com.agent.dock.provider.dto;

/**
 * 런타임 CLI 의 실행 파일 상태. resolvedPath/version/detail 은 확인할 수 없을 때 null 이다.
 * 저장하지 않고 조회 시점에 계산한다(폴더와 무관한 런타임 전역 정보다).
 */
public record CliStatusResponse(String resolvedPath, String version, boolean runnable, String detail) {
}
