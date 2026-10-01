package com.agent.dock.workspace.dto;

import com.agent.dock.workspace.domain.Workspace;
import java.util.List;

public record WorkspaceBrowseResult(String path, String parentPath, List<FsEntry> entries) {}
