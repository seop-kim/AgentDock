package com.agent.dock.execution.service;

import com.agent.dock.process.service.ProcessService;
import com.agent.dock.process.util.Executables;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * worktree 격리 규칙을 고정한다: 저장소 밖(워크스페이스의 형제) 형제 폴더, 루트 실행 id 기준 브랜치,
 * git 저장소가 아니면 조용히 포기하고 워크스페이스에서 실행한다.
 */
class WorktreeServiceTest {

    private final WorktreeService service = new WorktreeService(new ProcessService());

    @Test
    void namesWorktreeFolderNextToTheWorkspaceAndBranchAfterTheRootExecution(@TempDir Path tempDir) {
        File workspace = tempDir.resolve("AgentDock").toFile();

        File directory = WorktreeService.worktreeDirectory(workspace.getAbsolutePath(), 42L);

        assertThat(directory).isEqualTo(tempDir.resolve("AgentDock-wt").resolve("42").toFile());
        assertThat(WorktreeService.branchName(42L)).isEqualTo("agentdock/exec-42");
    }

    @Test
    void fallsBackWhenFolderIsNotAGitRepository(@TempDir Path tempDir) throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("plain"));

        assertThat(service.isGitRepository(folder.toString())).isFalse();
        assertThat(service.isGitRepository(tempDir.resolve("missing").toString())).isFalse();

        WorktreeService.Worktree worktree = service.create(folder.toString(), 7L);

        assertThat(worktree.created()).isFalse();
        assertThat(worktree.path()).isNull();
        assertThat(worktree.branch()).isNull();
        assertThat(worktree.reason()).contains("git 저장소가 아닙니다");
    }

    @Test
    void createsWorktreeInTheSiblingFolderAndRemovesItWithItsBranch(@TempDir Path tempDir) throws Exception {
        String git = Executables.locate("git");
        assumeTrue(git != null, "git 을 쓸 수 없어 건너뜁니다");
        Path repository = Files.createDirectories(tempDir.resolve("repo"));
        assumeTrue(git(git, repository, "init").exitCode() == 0, "git init 실패로 건너뜁니다");
        git(git, repository, "config", "user.email", "test@example.com");
        git(git, repository, "config", "user.name", "test");
        git(git, repository, "config", "commit.gpgsign", "false");
        Files.writeString(repository.resolve("README.md"), "hello");
        git(git, repository, "add", ".");
        assumeTrue(git(git, repository, "commit", "-m", "init").exitCode() == 0, "git commit 실패로 건너뜁니다");

        assertThat(service.isGitRepository(repository.toString())).isTrue();

        WorktreeService.Worktree worktree = service.create(repository.toString(), 42L);

        assertThat(worktree.created()).isTrue();
        assertThat(worktree.path()).isEqualTo(tempDir.resolve("repo-wt").resolve("42").toString());
        assertThat(worktree.branch()).isEqualTo("agentdock/exec-42");
        assertThat(Path.of(worktree.path())).isDirectory();

        WorktreeService.Removal removal = service.remove(worktree.path(), worktree.branch(), true);

        assertThat(removal.removed()).isTrue();
        assertThat(Path.of(worktree.path())).doesNotExist();
    }

    private static Git git(String git, Path cwd, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(git);
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        Process process = builder.start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Git(process.waitFor(), out + err);
    }

    private record Git(int exitCode, String text) {
    }
}
