package com.agent.dock.workspace.service;

import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.workspace.domain.Workspace;
import com.agent.dock.workspace.dto.CreateWorkspaceRequest;
import com.agent.dock.workspace.dto.FsEntry;
import com.agent.dock.workspace.dto.WorkspaceBrowseResult;
import com.agent.dock.workspace.dto.WorkspaceResponse;
import com.agent.dock.workspace.repository.WorkspaceRepository;
import com.agent.dock.workspace.util.WorkspaceFs;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
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
        return new WorkspaceBrowseResult(dir.getAbsolutePath(), parentPathOf(dir), listDirectories(dir));
    }

    private static List<FsEntry> listDirectories(File dir) {
        try (Stream<Path> stream = Files.list(dir.toPath())) {
            return stream
                    .filter(Files::isDirectory)
                    .map(p -> new FsEntry(p.getFileName().toString(), p.toString()))
                    .sorted(Comparator.comparing(FsEntry::name))
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private static String parentPathOf(File dir) {
        File parent = dir.getParentFile();
        return parent == null ? null : parent.getAbsolutePath();
    }

    /**
     * 드라이브 루트의 하위 항목 속성 조회는 Windows에서 첫 1회 30초까지 걸릴 수 있다
     * (예: C:\$AAA... 형태의 업데이트 잔여 폴더가 있는 경우). 프로세스 단위로 한 번만 지불하면
     * 이후에는 빠르므로, 기동 직후 백그라운드에서 미리 지불해 폴더 선택 UI가 멈추지 않게 한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    void warmUpDirectoryMetadata() {
        Thread.ofVirtual().name("workspace-fs-warmup").start(() -> {
            for (File root : File.listRoots()) {
                long started = System.currentTimeMillis();
                listDirectories(root.getAbsoluteFile());
                long took = System.currentTimeMillis() - started;
                if (took > 1000) {
                    log.info("warmed up {} in {} ms", root, took);
                }
            }
        });
    }
}
