package com.agent.dock.group.dto;

import jakarta.validation.constraints.NotNull;

public record AddGroupMemberRequest(@NotNull Long agentId) {}
