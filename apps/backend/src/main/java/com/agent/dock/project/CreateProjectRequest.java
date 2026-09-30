package com.agent.dock.project;

import jakarta.validation.constraints.NotBlank;

/** workspaceId 는 선택이다(나중에 프로젝트 상세에서 할당할 수 있다). */
public record CreateProjectRequest(
        @NotBlank String name,
        Long workspaceId,
        String description
) {
}
