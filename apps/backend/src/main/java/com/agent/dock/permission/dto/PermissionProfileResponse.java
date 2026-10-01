package com.agent.dock.permission.dto;

import com.agent.dock.permission.domain.PermissionProfile;
import java.time.Instant;

public record PermissionProfileResponse(
        Long id, String name, boolean fileRead, boolean fileWrite, boolean terminalExecute,
        boolean gitStatus, boolean gitDiff, boolean gitCommit, boolean gitPush,
        boolean dbRead, boolean dbWrite, boolean dbSchemaChange, boolean deploy,
        boolean externalNetworkAccess, Instant createdAt, Instant updatedAt
) {
    public static PermissionProfileResponse from(PermissionProfile p) {
        return new PermissionProfileResponse(p.getId(), p.getName(), p.isFileRead(), p.isFileWrite(),
                p.isTerminalExecute(), p.isGitStatus(), p.isGitDiff(), p.isGitCommit(), p.isGitPush(),
                p.isDbRead(), p.isDbWrite(), p.isDbSchemaChange(), p.isDeploy(), p.isExternalNetworkAccess(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
