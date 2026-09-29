package com.agent.dock.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTaskRequest(
        @NotNull Long projectId,
        @NotBlank String title,
        @NotBlank String prompt,
        Long groupId,
        Long agentId
) {}
