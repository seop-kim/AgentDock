package com.agent.dock.permission;

import jakarta.validation.constraints.NotBlank;

public record CreatePermissionProfileRequest(
        @NotBlank String name,
        Boolean fileRead,
        Boolean fileWrite,
        Boolean terminalExecute,
        Boolean gitStatus,
        Boolean gitDiff,
        Boolean gitCommit,
        Boolean gitPush,
        Boolean dbRead,
        Boolean dbWrite,
        Boolean dbSchemaChange,
        Boolean deploy,
        Boolean externalNetworkAccess
) {}
