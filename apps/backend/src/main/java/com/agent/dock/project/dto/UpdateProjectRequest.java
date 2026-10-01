package com.agent.dock.project.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 프로젝트 이름·설명 변경. description 을 주지 않으면(null) 기존 설명을 그대로 둔다
 * (화면은 이름만 바꾸는 경우가 많다).
 */
public record UpdateProjectRequest(
        @NotBlank String name,
        String description
) {
}
