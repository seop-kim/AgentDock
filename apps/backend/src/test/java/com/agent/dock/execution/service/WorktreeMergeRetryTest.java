package com.agent.dock.execution.service;

import com.agent.dock.execution.domain.ChangedFile;
import com.agent.dock.execution.domain.MergeStatus;
import com.agent.dock.process.service.ProcessService;
import com.agent.dock.process.util.Executables;
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
 * 다시 병합(`POST /executions/{id}/merge` → {@link WorktreeService#mergeBranch})을 실제 git 으로 고정한다:
 * 이미 커밋된 트리 브랜치를 메인 저장소의 현재 브랜치로 되돌리고, 메인 저장소가 더러우면(추적되는 변경)
 * 반쯤 병합된 상태를 남기지 않고 MANUAL, 정리한 뒤 다시 부르면 MERGED 다. 추적되지 않는 파일은 막지 않는다.
 */
class WorktreeMergeRetryTest {

    private final WorktreeService service = new WorktreeService(new ProcessService());

    @Test
    void mergesACommittedTreeBranchAfterTheMainTreeIsCleaned(@TempDir Path tempDir) throws Exception {
        String git = assumeGit();
        Path repository = initRepository(git, tempDir);
        WorktreeService.Worktree worktree = committedWorktree(git, tempDir, repository, 42L, "exec.txt");

        // 메인 저장소에 추적되는 변경을 남긴다 → 다시 병합도 MANUAL 이고 아무것도 바뀌지 않는다.
        Files.writeString(repository.resolve("file.txt"), "base\nuncommitted\n");
        String headBefore = run(git, repository, "rev-parse", "HEAD").text().strip();

        WorktreeService.TreeResult blocked = service.mergeBranch(worktree.path(), worktree.branch());

        assertThat(blocked.status()).isEqualTo(MergeStatus.MANUAL);
        assertThat(blocked.detail()).contains("커밋되지 않은");
        assertThat(run(git, repository, "rev-parse", "HEAD").text().strip()).isEqualTo(headBefore);
        assertThat(Files.exists(repository.resolve(".git/MERGE_HEAD"))).isFalse();

        // 사람이 정리한 뒤 다시 부르면 병합된다(커밋 sha 와 변경 파일도 함께 돌아온다).
        run(git, repository, "checkout", "--", "file.txt");

        WorktreeService.TreeResult merged = service.mergeBranch(worktree.path(), worktree.branch());

        assertThat(merged.status()).isEqualTo(MergeStatus.MERGED);
        assertThat(merged.commit()).isNotBlank();
        assertThat(merged.repositoryBranch())
                .isEqualTo(run(git, repository, "rev-parse", "--abbrev-ref", "HEAD").text().strip());
        assertThat(run(git, repository, "merge-base", "--is-ancestor", worktree.branch(), "HEAD").exitCode()).isZero();
        assertThat(Files.exists(repository.resolve("exec.txt"))).isTrue();
        assertThat(merged.changedFiles()).extracting(ChangedFile::path).contains("exec.txt");
    }

    @Test
    void mergesWhenTheOnlyUntrackedFilesAreToolFolders(@TempDir Path tempDir) throws Exception {
        String git = assumeGit();
        Path repository = initRepository(git, tempDir);
        WorktreeService.Worktree worktree = committedWorktree(git, tempDir, repository, 7L, "untracked.txt");
        Files.createDirectories(repository.resolve(".commandcode"));
        Files.writeString(repository.resolve(".commandcode/config.json"), "{}\n");

        WorktreeService.TreeResult result = service.mergeBranch(worktree.path(), worktree.branch());

        assertThat(result.status()).isEqualTo(MergeStatus.MERGED);
        assertThat(Files.exists(repository.resolve("untracked.txt"))).isTrue();
    }

    @Test
    void reportsManualWhenTheTreeBranchWasAlreadyRemoved(@TempDir Path tempDir) throws Exception {
        // 워크트리 폴더가 사라진 뒤(사람이 손으로 지운 경우) 다시 병합을 부르면 사유와 함께 MANUAL 이다.
        WorktreeService.TreeResult result = service.mergeBranch(tempDir.resolve("nowhere").toString(), "agentdock/exec-1");

        assertThat(result.status()).isEqualTo(MergeStatus.MANUAL);
        assertThat(result.detail()).isNotBlank();
    }

    // ── 실제 git 헬퍼 ─────────────────────────────────────────────────────────────

    /** 파일 두 개(file.txt, del.txt)가 커밋된 최소 저장소를 만든다. */
    private static Path initRepository(String git, Path tempDir) throws Exception {
        Path repository = Files.createDirectories(tempDir.resolve("repo"));
        run(git, repository, "init");
        run(git, repository, "config", "user.email", "test@example.com");
        run(git, repository, "config", "user.name", "test");
        run(git, repository, "config", "commit.gpgsign", "false");
        Files.writeString(repository.resolve("file.txt"), "base\n");
        Files.writeString(repository.resolve("del.txt"), "bye\n");
        run(git, repository, "add", ".");
        run(git, repository, "commit", "-m", "init");
        return repository;
    }

    /** 트리 브랜치에 커밋 하나가 남아 있는 상태(트리 실행이 끝난 뒤의 모습)를 만든다. */
    private WorktreeService.Worktree committedWorktree(String git, Path tempDir, Path repository, Long rootId,
                                                      String fileName) throws Exception {
        WorktreeService.Worktree worktree = service.create(repository.toString(), rootId);
        assumeTrue(worktree.created(), "worktree 생성 실패로 건너뜁니다");
        Path worktreePath = Path.of(worktree.path());
        Files.writeString(worktreePath.resolve(fileName), "결과\n");
        run(git, worktreePath, "add", "-A");
        run(git, worktreePath, "commit", "-m", "실행 #%d 작업 결과".formatted(rootId));
        return worktree;
    }

    private static String assumeGit() {
        String git = Executables.locate("git");
        assumeTrue(git != null, "git 을 쓸 수 없어 건너뜁니다");
        return git;
    }

    private static GitResult run(String git, Path cwd, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(git);
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        Process process = builder.start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new GitResult(process.waitFor(), out + err);
    }

    private record GitResult(int exitCode, String text) {
    }
}
