package com.agent.dock.execution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateExecutionRequest(
        @NotNull Long agentId,
        @NotNull Long projectId,
        @NotBlank String prompt
) {}
