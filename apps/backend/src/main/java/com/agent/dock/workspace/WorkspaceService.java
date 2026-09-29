package com.agent.dock.workspace;

import com.agent.dock.common.BadRequestException;
import com.agent.dock.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final WorkspaceRepository repository;
    private final WorkspaceFs workspaceFs;

    public List<WorkspaceResponse> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(WorkspaceResponse::from).toList();
    }

    public WorkspaceResponse create(CreateWorkspaceRequest request) {
        if (workspaceFs.isUncPath(request.path())) {
            throw new BadRequestException("Network path (UNC) is not allowed");
        }
        File dir = new File(request.path()).getAbsoluteFile();
        if (!dir.exists()) {
            throw new BadRequestException("Path \"" + dir.getPath() + "\" does not exist");
        }
        if (!dir.isDirectory()) {
            throw new BadRequestException("Path \"" + dir.getPath() + "\" is not a directory");
        }
        Workspace workspace = new Workspace();
        workspace.setName(request.name());
        workspace.setPath(dir.getAbsolutePath());
        workspace.setDescription(request.description());
        return WorkspaceResponse.from(repository.save(workspace));
    }

    public WorkspaceBrowseResult browse(String path) {
        if (path == null || path.isBlank()) {
            return new WorkspaceBrowseResult(null, null, workspaceFs.listRoots());
        }
        if (workspaceFs.isUncPath(path)) {
            throw new BadRequestException("Network path (UNC) is not allowed");
        }
        File dir = new File(path).getAbsoluteFile();
        if (!dir.exists()) {
            throw new NotFoundException("Path \"" + dir.getPath() + "\" does not exist");
        }
        if (!dir.isDirectory()) {
            throw new BadRequestException("Path \"" + dir.getPath() + "\" is not a directory");
        }
        File[] children = dir.listFiles(File::isDirectory);
        List<FsEntry> entries = children == null ? List.of() :
                Arrays.stream(children)
                        .map(f -> new FsEntry(f.getName(), f.getAbsolutePath()))
                        .sorted(Comparator.comparing(FsEntry::name))
                        .toList();
        File parent = dir.getParentFile();
        String parentPath = parent == null ? null : parent.getAbsolutePath();
        return new WorkspaceBrowseResult(dir.getAbsolutePath(), parentPath, entries);
    }
}
