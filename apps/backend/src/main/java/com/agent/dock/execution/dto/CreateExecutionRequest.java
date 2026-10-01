package com.agent.dock.execution.dto;

import com.agent.dock.execution.domain.Execution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateExecutionRequest(
        @NotNull Long agentId,
        @NotNull Long projectId,
        @NotBlank String prompt
) {}
