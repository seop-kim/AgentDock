package com.agent.dock.workspace.dto;

import com.agent.dock.workspace.domain.Workspace;
import jakarta.validation.constraints.NotBlank;

public record CreateWorkspaceRequest(@NotBlank String name, @NotBlank String path, String description) {}
