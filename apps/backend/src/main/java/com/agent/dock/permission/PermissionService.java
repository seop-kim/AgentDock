package com.agent.dock.permission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final PermissionProfileRepository repository;

    public List<PermissionProfileResponse> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(PermissionProfileResponse::from).toList();
    }

    public PermissionProfileResponse create(CreatePermissionProfileRequest request) {
        PermissionProfile profile = new PermissionProfile();
        profile.setName(request.name());
        profile.setFileRead(Boolean.TRUE.equals(request.fileRead()));
        profile.setFileWrite(Boolean.TRUE.equals(request.fileWrite()));
        profile.setTerminalExecute(Boolean.TRUE.equals(request.terminalExecute()));
        profile.setGitStatus(request.gitStatus() == null || request.gitStatus());
        profile.setGitDiff(Boolean.TRUE.equals(request.gitDiff()));
        profile.setGitCommit(Boolean.TRUE.equals(request.gitCommit()));
        profile.setGitPush(Boolean.TRUE.equals(request.gitPush()));
        profile.setDbRead(Boolean.TRUE.equals(request.dbRead()));
        profile.setDbWrite(Boolean.TRUE.equals(request.dbWrite()));
        profile.setDbSchemaChange(Boolean.TRUE.equals(request.dbSchemaChange()));
        profile.setDeploy(Boolean.TRUE.equals(request.deploy()));
        profile.setExternalNetworkAccess(Boolean.TRUE.equals(request.externalNetworkAccess()));
        return PermissionProfileResponse.from(repository.save(profile));
    }

    /** Backend Tool Layer enforcement: Prompt 설명이 아니라 여기서 실제로 허용 여부를 결정한다. */
    public boolean isAllowed(PermissionProfile profile, PermissionAction action) {
        return switch (action) {
            case FILE_READ -> profile.isFileRead();
            case FILE_WRITE -> profile.isFileWrite();
            case TERMINAL_EXECUTE -> profile.isTerminalExecute();
            case GIT_STATUS -> profile.isGitStatus();
            case GIT_DIFF -> profile.isGitDiff();
            case GIT_COMMIT -> profile.isGitCommit();
            case GIT_PUSH -> profile.isGitPush();
            case DB_READ -> profile.isDbRead();
            case DB_WRITE -> profile.isDbWrite();
            case DB_SCHEMA_CHANGE -> profile.isDbSchemaChange();
            case DEPLOY -> profile.isDeploy();
            case EXTERNAL_NETWORK_ACCESS -> profile.isExternalNetworkAccess();
        };
    }
}
