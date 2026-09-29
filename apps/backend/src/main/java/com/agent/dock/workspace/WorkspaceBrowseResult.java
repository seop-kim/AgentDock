package com.agent.dock.workspace;

import java.util.List;

public record WorkspaceBrowseResult(String path, String parentPath, List<FsEntry> entries) {}
