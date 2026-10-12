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
 * 트리 결과 되돌리기 규칙을 실제 git 으로 고정한다: 깨끗한 메인 저장소면 자동 병합(MERGED),
 * 더러우면 반쯤 병합된 상태를 남기지 않고 MANUAL, 커밋할 것이 없으면 빈 커밋 없이 NONE.
 * changed_files 는 `git diff --name-status <base>...HEAD` 를 파싱한 `{status, path}` 다.
 */
class WorktreeResultCollectTest {

    private final WorktreeService service = new WorktreeService(new ProcessService());

    @Test
    void commitsAndMergesIntoACleanMainRepository(@TempDir Path tempDir) throws Exception {
        String git = assumeGit();
        Path repository = initRepository(git, tempDir);

        WorktreeService.Worktree worktree = service.create(repository.toString(), 42L);
        assumeTrue(worktree.created(), "worktree 생성 실패로 건너뜁니다");
        Path worktreePath = Path.of(worktree.path());
        Files.writeString(worktreePath.resolve("file.txt"), "base\nchanged\n");
        Files.writeString(worktreePath.resolve("new.txt"), "new\n");

        WorktreeService.TreeResult result = service.collect(repository.toString(), worktree.path(),
                worktree.branch(), "실행 요약 제목", "실행 #42\n참여: 마스터");

        assertThat(result.status()).isEqualTo(MergeStatus.MERGED);
        assertThat(result.commit()).isNotBlank();
        assertThat(result.repositoryBranch()).isEqualTo(run(git, repository, "rev-parse", "--abbrev-ref", "HEAD")
                .text().strip());
        // 트리 브랜치의 커밋이 메인 저장소 현재 브랜치로 실제로 들어왔다.
        assertThat(run(git, repository, "merge-base", "--is-ancestor", worktree.branch(), "HEAD").exitCode()).isZero();
        assertThat(run(git, repository, "show", "HEAD:new.txt").text()).contains("new");
        assertThat(result.changedFiles()).extracting(ChangedFile::path)
                .containsExactlyInAnyOrder("file.txt", "new.txt");
    }

    /**
     * 추적되지 않는 파일(`?? .commandcode/` 같은 도구 폴더)은 병합을 막지 않는다 — 깨끗함은 **추적되는 파일의 변경만**
     * 본다(`git status --porcelain --untracked-files=no`). 예전에는 이것 하나 때문에 모든 트리가 MANUAL 로 끝났다.
     */
    @Test
    void mergesEvenWhenTheMainRepositoryHasUntrackedFiles(@TempDir Path tempDir) throws Exception {
        String git = assumeGit();
        Path repository = initRepository(git, tempDir);

        WorktreeService.Worktree worktree = service.create(repository.toString(), 42L);
        assumeTrue(worktree.created(), "worktree 생성 실패로 건너뜁니다");
        Files.writeString(Path.of(worktree.path()).resolve("new.txt"), "new\n");
        // 추적되지 않는 폴더를 메인 저장소에 남긴다(도구 폴더가 만드는 실제 상황).
        Files.createDirectories(repository.resolve(".commandcode"));
        Files.writeString(repository.resolve(".commandcode/config.json"), "{}\n");

        WorktreeService.TreeResult result = service.collect(repository.toString(), worktree.path(),
                worktree.branch(), "실행 요약", "실행 #42\n참여: 마스터");

        assertThat(result.status()).isEqualTo(MergeStatus.MERGED);
        assertThat(run(git, repository, "status", "--porcelain", "--untracked-files=no").text()).isBlank();
        assertThat(Files.exists(repository.resolve("new.txt"))).isTrue();
    }

    @Test
    void recordsManualWithoutLeavingAHalfMergeWhenTheMainTreeIsDirty(@TempDir Path tempDir) throws Exception {
        String git = assumeGit();
        Path repository = initRepository(git, tempDir);

        WorktreeService.Worktree worktree = service.create(repository.toString(), 42L);
        assumeTrue(worktree.created(), "worktree 생성 실패로 건너뜁니다");
        Files.writeString(Path.of(worktree.path()).resolve("file.txt"), "base\nworktree change\n");
        // 메인 저장소에 커밋되지 않은 변경을 남긴다 → 자동 병합하면 안 된다.
        Files.writeString(repository.resolve("del.txt"), "uncommitted\n");
        String headBefore = run(git, repository, "rev-parse", "HEAD").text().strip();

        WorktreeService.TreeResult result = service.collect(repository.toString(), worktree.path(),
                worktree.branch(), "실행 요약", "실행 #42\n참여: 마스터");

        assertThat(result.status()).isEqualTo(MergeStatus.MANUAL);
        assertThat(result.commit()).isNotBlank();
        assertThat(result.detail()).contains("커밋되지 않은");
        // 반쯤 병합된 상태를 남기지 않는다: HEAD 그대로, MERGE_HEAD 없음, 충돌 없음.
        assertThat(run(git, repository, "rev-parse", "HEAD").text().strip()).isEqualTo(headBefore);
        assertThat(Files.exists(repository.resolve(".git/MERGE_HEAD"))).isFalse();
        String status = run(git, repository, "status", "--porcelain").text();
        assertThat(status).contains("del.txt").doesNotContain("UU").doesNotContain("AA");
        // 트리 브랜치에는 커밋이 남아 있다(작업이 사라지지 않는다).
        assertThat(run(git, repository, "rev-parse", "--verify", worktree.branch()).exitCode()).isZero();
    }

    @Test
    void recordsNoneWithoutCreatingAnEmptyCommit(@TempDir Path tempDir) throws Exception {
        String git = assumeGit();
        Path repository = initRepository(git, tempDir);

        WorktreeService.Worktree worktree = service.create(repository.toString(), 7L);
        assumeTrue(worktree.created(), "worktree 생성 실패로 건너뜁니다");
        String branchHeadBefore = run(git, repository, "rev-parse", worktree.branch()).text().strip();

        WorktreeService.TreeResult result = service.collect(repository.toString(), worktree.path(),
                worktree.branch(), "요약", "실행 #7\n참여: 마스터");

        assertThat(result.status()).isEqualTo(MergeStatus.NONE);
        assertThat(result.commit()).isNull();
        assertThat(result.changedFiles()).isEmpty();
        // 빈 커밋을 만들지 않는다: 브랜치가 그대로다.
        assertThat(run(git, repository, "rev-parse", worktree.branch()).text().strip()).isEqualTo(branchHeadBefore);
    }

    @Test
    void parsesChangedFilesWithTheirStatus(@TempDir Path tempDir) throws Exception {
        String git = assumeGit();
        Path repository = initRepository(git, tempDir);

        WorktreeService.Worktree worktree = service.create(repository.toString(), 5L);
        assumeTrue(worktree.created(), "worktree 생성 실패로 건너뜁니다");
        Path worktreePath = Path.of(worktree.path());
        Files.writeString(worktreePath.resolve("file.txt"), "base\nmodified\n");   // M
        Files.writeString(worktreePath.resolve("added.txt"), "added\n");           // A
        Files.delete(worktreePath.resolve("del.txt"));                             // D

        WorktreeService.TreeResult result = service.collect(repository.toString(), worktree.path(),
                worktree.branch(), "요약", "실행 #5\n참여: 마스터");

        assertThat(result.status()).isEqualTo(MergeStatus.MERGED);
        assertThat(result.changedFiles()).containsExactlyInAnyOrder(
                new ChangedFile("M", "file.txt"),
                new ChangedFile("A", "added.txt"),
                new ChangedFile("D", "del.txt"));
    }

    // ── 실제 git 헬퍼 ─────────────────────────────────────────────────────────────

    private static String assumeGit() {
        String git = Executables.locate("git");
        assumeTrue(git != null, "git 을 쓸 수 없어 건너뜁니다");
        return git;
    }

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
