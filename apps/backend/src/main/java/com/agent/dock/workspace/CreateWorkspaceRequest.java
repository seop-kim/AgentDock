package com.agent.dock.workspace;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkspaceRequest(@NotBlank String name, @NotBlank String path, String description) {}
