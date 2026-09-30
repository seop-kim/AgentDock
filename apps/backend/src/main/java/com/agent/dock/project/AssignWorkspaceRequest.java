package com.agent.dock.project;

import jakarta.validation.constraints.NotNull;

/** 프로젝트에 워크스페이스를 할당한다. 첫 할당이거나 isDefault 면 기본 워크스페이스가 된다. */
public record AssignWorkspaceRequest(@NotNull Long workspaceId, Boolean isDefault) {
}
