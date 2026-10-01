package com.agent.dock.task.dto;

import com.agent.dock.task.domain.Task;
import jakarta.validation.constraints.NotBlank;

/**
 * 프로젝트 채팅에서 보내는 명령. 대상을 주지 않으면 프로젝트 마스터가 받는다.
 */
public record CommandRequest(
        @NotBlank String text,
        Long targetAgentId,
        Long groupId
) {
}
