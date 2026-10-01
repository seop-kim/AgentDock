package com.agent.dock.execution.service;

import com.agent.dock.process.service.ProcessService;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 실행 트리를 git worktree 로 격리한다.
 *
 * <p>명령 하나(루트 실행)마다 워크스페이스 저장소의 worktree 를 하나 만들어, 그 트리 전체(루트 + 자식)가 그 안에서 돈다.
 * 자식의 편집을 부모가 봐야 하므로 자식은 부모와 **같은** worktree 를 쓰고, 서로 다른 명령(다른 루트)만 격리된다.
 * worktree 는 저장소 **밖** — 워크스페이스 폴더의 형제 폴더 `<폴더>-wt/<루트실행id>` — 에 두어 저장소의
 * `git status` 와 첨부 파일 목록을 더럽히지 않는다. 브랜치는 `agentdock/exec-<루트실행id>` 다.
 *
 * <p>워크트리·브랜치의 **자동 삭제는 없다**(정리는 `DELETE /executions/{id}/worktree` 로 사람이 판단해 부른다).
 *
 * <p>git 은 {@link ProcessService} 로 실행한다(셸 미경유 — {@code ProcessService} 가 {@code Executables} 로 실제 실행 파일을 찾는다).
 * 실패는 실행을 막지 않는다 — 만들지 못하면 비어 있는 결과와 사유를 돌려주고 호출부가 워크스페이스에서 계속 돌린다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorktreeService {

    /** 저장소 밖 worktree 폴더 접미사: `<워크스페이스폴더>-wt`. */
    static final String FOLDER_SUFFIX = "-wt";
    /** worktree 브랜치 접두사: `agentdock/exec-<루트실행id>`. */
    static final String BRANCH_PREFIX = "agentdock/exec-";

    private final ProcessService processService;

    /** 만들어진 worktree. 실패하면 path/branch 가 비고 reason 에 사유가 담긴다(실행은 워크스페이스에서 계속된다). */
    public record Worktree(String path, String branch, String reason) {
        public boolean created() {
            return path != null && !path.isBlank();
        }
    }

    /** 정리 결과. removed 가 false 면 detail 에 사유가 담긴다. */
    public record Removal(boolean removed, String detail) {
    }

    /**
     * 실행 트리 하나가 쓸 worktree 를 만든다. 워크스페이스가 git 저장소가 아니거나 git 이 실패하면 만들지 않고 사유를 돌려준다.
     *
     * @param workspacePath  프로젝트 기본 워크스페이스 폴더(저장소 루트)
     * @param rootExecutionId 트리 루트 실행 id(폴더 이름과 브랜치 이름의 기준)
     */
    public Worktree create(String workspacePath, Long rootExecutionId) {
        if (workspacePath == null || workspacePath.isBlank()) {
            return new Worktree(null, null, "워크스페이스 경로가 없습니다");
        }
        if (!isGitRepository(workspacePath)) {
            return new Worktree(null, null, "git 저장소가 아닙니다");
        }
        File repository = new File(workspacePath).getAbsoluteFile();
        File directory = worktreeDirectory(workspacePath, rootExecutionId);
        String branch = branchName(rootExecutionId);
        try {
            if (directory.isDirectory() && !isDirectoryEmpty(directory)) {
                return new Worktree(null, null, "워크트리 폴더가 이미 있습니다: " + directory.getAbsolutePath());
            }
            File parent = directory.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            GitResult added = git(List.of("worktree", "add", "-b", branch, directory.getAbsolutePath()),
                    repository.getAbsolutePath());
            if (added.exitCode() != 0) {
                return new Worktree(null, null, added.text());
            }
            return new Worktree(directory.getAbsolutePath(), branch, null);
        } catch (Exception ex) {
            log.warn("worktree 생성 실패: {}", ex.getMessage());
            return new Worktree(null, null, String.valueOf(ex.getMessage()));
        }
    }

    /** worktree 를 지운다(사람이 판단해 부른다). deleteBranch 면 전용 브랜치도 함께 지운다. */
    public Removal remove(String worktreePath, String branch, boolean deleteBranch) {
        if (worktreePath == null || worktreePath.isBlank()) {
            return new Removal(false, "워크트리 경로가 없습니다");
        }
        try {
            // `git worktree remove` 는 그 worktree 안이 아니라 메인 저장소에서 실행해야 한다(common-dir 로 찾는다).
            String repository = mainWorktree(worktreePath).orElse(null);
            if (repository == null) {
                return new Removal(false, "저장소를 찾지 못했습니다: " + worktreePath);
            }
            GitResult removed = git(List.of("worktree", "remove", "--force", worktreePath), repository);
            if (removed.exitCode() != 0) {
                return new Removal(false, removed.text());
            }
            String detail = null;
            if (deleteBranch && branch != null && !branch.isBlank()) {
                GitResult deleted = git(List.of("branch", "-D", branch), repository);
                if (deleted.exitCode() != 0) {
                    detail = "워크트리는 지웠지만 브랜치를 지우지 못했습니다: " + deleted.text();
                }
            }
            git(List.of("worktree", "prune"), repository);
            return new Removal(true, detail);
        } catch (Exception ex) {
            log.warn("worktree 삭제 실패: {}", ex.getMessage());
            return new Removal(false, String.valueOf(ex.getMessage()));
        }
    }

    /** 폴더가 git 저장소(작업 트리)인지 확인한다. git 이 없거나 실패하면 false. */
    public boolean isGitRepository(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            GitResult result = git(List.of("rev-parse", "--is-inside-work-tree"), path);
            return result.exitCode() == 0 && result.text().strip().equalsIgnoreCase("true");
        } catch (Exception ex) {
            log.warn("git 저장소 확인 실패: {}", ex.getMessage());
            return false;
        }
    }

    /** 저장소 밖(워크스페이스의 형제) `<워크스페이스폴더>-wt/<루트실행id>` 경로. */
    static File worktreeDirectory(String workspacePath, Long rootExecutionId) {
        File workspace = new File(workspacePath).getAbsoluteFile();
        File parent = workspace.getParentFile() == null ? workspace : workspace.getParentFile();
        return new File(new File(parent, workspace.getName() + FOLDER_SUFFIX), String.valueOf(rootExecutionId));
    }

    /** worktree 전용 브랜치 이름. */
    static String branchName(Long rootExecutionId) {
        return BRANCH_PREFIX + rootExecutionId;
    }

    /** worktree 가 속한 (메인) 저장소 경로. common-dir(git 디렉터리)의 부모가 저장소 루트다. */
    private Optional<String> mainWorktree(String worktreePath) throws Exception {
        GitResult result = git(List.of("rev-parse", "--path-format=absolute", "--git-common-dir"), worktreePath);
        if (result.exitCode() != 0) {
            return Optional.empty();
        }
        File commonDir = new File(result.text().strip());
        return Optional.ofNullable(commonDir.getParentFile()).map(File::getAbsolutePath);
    }

    private record GitResult(int exitCode, String text) {
    }

    /** git 한 번. 셸을 쓰지 않고 stdout/stderr 를 함께 모아 돌려준다(짧은 명령이라 교착이 없다). */
    private GitResult git(List<String> args, String cwd) throws Exception {
        Process process = processService.spawn("worktree-" + UUID.randomUUID(), "git", args, cwd);
        AtomicReference<String> stdout = new AtomicReference<>("");
        AtomicReference<String> stderr = new AtomicReference<>("");
        CompletableFuture<Void> out = CompletableFuture.runAsync(() -> stdout.set(read(process.getInputStream())));
        CompletableFuture<Void> err = CompletableFuture.runAsync(() -> stderr.set(read(process.getErrorStream())));
        int exitCode = process.waitFor();
        CompletableFuture.allOf(out, err).join();
        return new GitResult(exitCode, (stdout.get() + stderr.get()).strip());
    }

    private static String read(InputStream input) {
        try {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private static boolean isDirectoryEmpty(File directory) {
        String[] children = directory.list();
        return children == null || children.length == 0;
    }
}
