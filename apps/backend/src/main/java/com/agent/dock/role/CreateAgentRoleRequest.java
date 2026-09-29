package com.agent.dock.role;

import jakarta.validation.constraints.NotBlank;

public record CreateAgentRoleRequest(@NotBlank String name, String description) {}
