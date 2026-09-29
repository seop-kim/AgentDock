package com.agent.dock.group;

import jakarta.validation.constraints.NotNull;

public record AddGroupMemberRequest(@NotNull Long agentId) {}
