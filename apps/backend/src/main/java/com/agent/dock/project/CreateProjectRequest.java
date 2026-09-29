package com.agent.dock.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProjectRequest(
        @NotBlank String name,
        @NotNull Long workspaceId,
        String description
) {}
