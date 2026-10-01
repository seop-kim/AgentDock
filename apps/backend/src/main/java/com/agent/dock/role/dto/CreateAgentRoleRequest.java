package com.agent.dock.role.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAgentRoleRequest(@NotBlank String name, String description) {}
