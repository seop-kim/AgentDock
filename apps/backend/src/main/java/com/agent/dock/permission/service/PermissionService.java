package com.agent.dock.permission.service;

import com.agent.dock.permission.domain.PermissionAction;
import com.agent.dock.permission.domain.PermissionProfile;
import com.agent.dock.permission.dto.PermissionProfileResponse;
import com.agent.dock.permission.repository.PermissionProfileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final PermissionProfileRepository repository;

    public List<PermissionProfileResponse> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(PermissionProfileResponse::from).toList();
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
