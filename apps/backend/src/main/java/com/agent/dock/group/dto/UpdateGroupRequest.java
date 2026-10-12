package com.agent.dock.group.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 전체 교체(PUT) 의미: leaderAgentId 가 null 이면 리더를 해제한다.
 * sharedNote 는 **보내지 않으면(null) 기존 값을 유지**한다 — 프롬프트만 저장할 때 노트가 지워지지 않게.
 * 빈 문자열을 보내면 노트를 지운다.
 */
public record UpdateGroupRequest(
        @NotBlank String name,
        String description,
        String prompt,
        String sharedNote,
        Long leaderAgentId
) {}
