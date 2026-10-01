package com.agent.dock.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 에이전트 수정(전체 교체). 소속 프로젝트와 배치(placed·좌표)는 바꾸지 않는다 — 배치는 layout API 가 맡는다.
 * 런타임(providerId)은 켜져 있고 논리 삭제되지 않은 것만 받는다(생성과 같은 규칙).
 */
public record UpdateAgentRequest(
        @NotBlank String name,
        @NotNull Long roleId,
        @NotNull Long permissionProfileId,
        @NotNull Long providerId,
        String model,
        String mode,
        String persona
) {
}
