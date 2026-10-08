package com.agent.dock.execution.service;

import com.agent.dock.execution.domain.ChangedFile;
import com.agent.dock.execution.domain.MergeStatus;
import com.agent.dock.process.service.ProcessService;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.agent.dock.runtime.service.StreamPumps;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 실행 트리를 git worktree 로 격리하고, 트리가 끝나면 그 결과를 메인 저장소로 되돌린다.
 *
 * <p>명령 하나(루트 실행)마다 워크스페이스 저장소의 worktree 를 하나 만들어, 그 트리 전체(루트 + 자식)가 그 안에서 돈다.
 * 자식의 편집을 부모가 봐야 하므로 자식은 부모와 **같은** worktree 를 쓰고, 서로 다른 명령(다른 루트)만 격리된다.
 * worktree 는 저장소 **밖** — 워크스페이스 폴더의 형제 폴더 `<폴더>-wt/<루트실행id>` — 에 두어 저장소의
 * `git status` 와 첨부 파일 목록을 더럽히지 않는다. 브랜치는 `agentdock/exec-<루트실행id>` 다.
 *
 * <p>트리가 끝나면 {@link #collect} 가 그 worktree 의 변경을 **커밋 하나**로 남기고, 메인 저장소가 깨끗하면
 * `git merge --no-ff` 로 되돌린다. 더럽거나 충돌하면 `git merge --abort` 로 절대 반쯤 병합된 상태를 남기지 않고
 * 사유와 함께 수동 병합 대상으로 넘긴다. 워크트리·브랜치의 **자동 삭제는 없다**(정리는 `DELETE /executions/{id}/worktree`).
 *
 * <p>깨끗함은 **추적되는 파일의 변경만** 본다(`git status --porcelain --untracked-files=no`). 추적되지 않는 파일
 * (`?? .commandcode/` 같은 도구 폴더) 하나 때문에 자동 병합이 막히면 안 된다 — git 이 실제로 병합을 거부하면
 * 그때 abort + MANUAL 로 간다.
 *
 * <p>자동 병합이 MANUAL 로 끝난 뒤 사람이 변경을 정리했으면 {@link #mergeBranch} 로 **다시 병합**할 수 있다.
 * 커밋은 만들지 않고 트리 브랜치를 그대로 병합하며, 규칙(깨끗함 확인·충돌 시 abort)은 자동 병합과 같다.
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

    /**
     * 병합은 **한 번에 하나만** 한다(공정 세마포어). 같은 메인 저장소에 여러 트리가 동시에 끝날 수 있어,
     * `git merge` 가 겹치면 안 된다. 커밋은 각자 자기 worktree 에서 하므로 직렬화하지 않는다.
     */
    private final Semaphore mergeLock = new Semaphore(1, true);

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
     * 트리 끝에서 되돌린 결과.
     *
     * @param status           MERGED(자동 병합) / MANUAL(사람이 병합해야 함) / NONE(커밋할 변경 없음)
     * @param commit           트리 브랜치에 남긴 커밋의 짧은 sha. 커밋이 없으면 null
     * @param detail           자동 병합하지 못한 사유(MANUAL 이면 채워진다)
     * @param repositoryBranch 병합한 메인 저장소의 브랜치 이름(MERGED 일 때만 채워진다)
     * @param changedFiles     트리가 바꾼 파일 목록(`git diff --name-status <base>...HEAD`)
     */
    public record TreeResult(MergeStatus status, String commit, String detail, String repositoryBranch,
                             List<ChangedFile> changedFiles) {

        static TreeResult none(String detail) {
            return new TreeResult(MergeStatus.NONE, null, detail, null, List.of());
        }
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

    /**
     * 트리 끝에서 그 워크트리의 변경을 트리 브랜치에 **커밋 하나**로 남기고, 메인 저장소로 되돌린다.
     *
     * <p>커밋 메시지는 루트 실행의 최종 요약이다. body 에는 실행 id·참여 에이전트·변경 파일 수가 들어가고,
     * {@code Co-Authored-By} 트레일러는 붙이지 않는다(저장소 규칙). 커밋할 변경이 없으면 빈 커밋을 만들지 않고 NONE 이다.
     *
     * <p>되돌리기는 메인 저장소 작업 트리가 깨끗할 때만 {@code git merge --no-ff} 로 한다. 더럽거나 충돌하면
     * {@code git merge --abort} 로 되돌리고 사유와 함께 MANUAL 이다(반쯤 병합된 상태를 남기지 않는다).
     * 병합은 {@link #mergeLock} 으로 전역 직렬화한다(여러 트리가 동시에 끝날 수 있다).
     *
     * @param repositoryPath 메인 저장소 경로(워크스페이스 루트)
     * @param worktreePath   이 트리가 돈 worktree 경로
     * @param branch         트리 브랜치(`agentdock/exec-<루트id>`)
     * @param message        커밋 제목(루트 실행의 최종 요약)
     * @param body           커밋 본문 앞부분(실행 id·참여 에이전트). 변경 파일 수는 여기서 덧붙인다
     */
    public TreeResult collect(String repositoryPath, String worktreePath, String branch, String message, String body) {
        if (worktreePath == null || worktreePath.isBlank()) {
            return TreeResult.none("워크트리가 없어 되돌릴 결과가 없습니다");
        }
        try {
            GitResult status = git(List.of("status", "--porcelain"), worktreePath);
            if (status.exitCode() != 0) {
                return TreeResult.none(status.text());
            }
            if (status.text().isBlank()) {
                return TreeResult.none("커밋할 변경이 없습니다");
            }
            int pending = countLines(status.text());

            GitResult base = git(List.of("rev-parse", "HEAD"), worktreePath);
            if (base.exitCode() != 0) {
                return TreeResult.none(base.text());
            }
            if (git(List.of("add", "-A"), worktreePath).exitCode() != 0) {
                return TreeResult.none("변경을 스테이징하지 못했습니다");
            }
            GitResult commit = git(List.of("commit", "-m", message, "-m", body + "\n변경 파일 " + pending + "개"),
                    worktreePath);
            if (commit.exitCode() != 0) {
                return new TreeResult(MergeStatus.MANUAL, null, "커밋하지 못했습니다: " + commit.text(), null, List.of());
            }
            String sha = shortSha(worktreePath);
            List<ChangedFile> files = changedFiles(worktreePath, base.text().strip());
            return merge(repositoryPath, branch, sha, files);
        } catch (Exception ex) {
            log.warn("트리 결과 수집 실패: {}", ex.getMessage());
            return TreeResult.none(String.valueOf(ex.getMessage()));
        }
    }

    /**
     * 이미 커밋된 트리 브랜치를 메인 저장소의 **현재 브랜치**로 다시 병합한다.
     *
     * <p>자동 병합이 MANUAL(더러운 작업 트리·충돌)로 끝난 뒤 사람이 변경을 정리했을 때 부른다. 커밋은 만들지 않고
     * 트리 브랜치의 커밋을 그대로 병합하며, 깨끗함 확인(추적되는 파일만)과 실패 시 {@code git merge --abort} 규칙은
     * 자동 병합과 같다. 트리 브랜치가 이미 병합돼 있으면 git 이 할 일이 없다고 답하고 MERGED 로 돌아온다.
     *
     * @param worktreePath 이 트리가 돈 worktree 경로(메인 저장소를 여기서 찾는다)
     * @param branch       트리 브랜치(`agentdock/exec-<루트id>`)
     */
    public TreeResult mergeBranch(String worktreePath, String branch) {
        if (worktreePath == null || worktreePath.isBlank()) {
            return new TreeResult(MergeStatus.MANUAL, null, "워크트리 경로가 없습니다", null, List.of());
        }
        if (branch == null || branch.isBlank()) {
            return new TreeResult(MergeStatus.MANUAL, null, "트리 브랜치가 없습니다", null, List.of());
        }
        try {
            String repository = mainWorktree(worktreePath).orElse(null);
            if (repository == null) {
                return new TreeResult(MergeStatus.MANUAL, null,
                        "저장소를 찾지 못했습니다: " + worktreePath, null, List.of());
            }
            // 병합 전에 구한다: 병합이 끝나면 브랜치와 HEAD 가 같아져 diff 가 비어 버린다.
            String commit = shortSha(repository, branch);
            List<ChangedFile> files = changedFilesOfBranch(repository, branch);
            return merge(repository, branch, commit, files);
        } catch (Exception ex) {
            log.warn("트리 브랜치 다시 병합 실패: {}", ex.getMessage());
            return new TreeResult(MergeStatus.MANUAL, null, String.valueOf(ex.getMessage()), null, List.of());
        }
    }

    /** 메인 저장소로 되돌린다. 병합은 전역으로 직렬화한다(커밋은 각자 자기 worktree 에서 이미 끝났다). */
    private TreeResult merge(String repositoryPath, String branch, String commit, List<ChangedFile> files)
            throws Exception {
        if (repositoryPath == null || repositoryPath.isBlank()) {
            return new TreeResult(MergeStatus.MANUAL, commit, "메인 저장소 경로를 찾지 못했습니다", null, files);
        }
        mergeLock.acquire();
        try {
            // 추적되는 파일의 변경만 본다. 추적되지 않는 파일(`?? .commandcode/` 같은 도구 폴더)은 병합을 막지 않는다.
            GitResult status = git(List.of("status", "--porcelain", "--untracked-files=no"), repositoryPath);
            if (status.exitCode() != 0) {
                return new TreeResult(MergeStatus.MANUAL, commit,
                        "메인 저장소 상태를 확인하지 못했습니다: " + status.text(), null, files);
            }
            if (!status.text().isBlank()) {
                return new TreeResult(MergeStatus.MANUAL, commit,
                        "메인 저장소에 커밋되지 않은 변경이 있습니다: " + firstLines(status.text(), 3), null, files);
            }
            String repositoryBranch = branchOf(repositoryPath);
            GitResult merged = git(List.of("merge", "--no-ff", branch, "-m", "Merge branch '" + branch + "'"),
                    repositoryPath);
            if (merged.exitCode() == 0) {
                return new TreeResult(MergeStatus.MERGED, commit, null, repositoryBranch, files);
            }
            // 충돌/실패: 절대 반쯤 병합된 상태로 두지 않는다.
            String conflicts = firstLines(
                    git(List.of("diff", "--name-only", "--diff-filter=U"), repositoryPath).text(), 5);
            git(List.of("merge", "--abort"), repositoryPath);
            String reason = conflicts.isBlank() ? merged.text() : conflicts;
            return new TreeResult(MergeStatus.MANUAL, commit, "자동 병합에 실패했습니다: " + reason, null, files);
        } finally {
            mergeLock.release();
        }
    }

    /** `git diff --name-status <base>...HEAD` 한 줄씩 파싱한다. 이름 바꾸기(R)/복사(C)는 마지막 필드가 새 경로다. */
    private List<ChangedFile> changedFiles(String worktreePath, String base) throws Exception {
        return changedFiles(worktreePath, base, "HEAD");
    }

    /** `git diff --name-status <base>...<head>` 를 파싱한다. */
    private List<ChangedFile> changedFiles(String cwd, String base, String head) throws Exception {
        GitResult diff = git(List.of("diff", "--name-status", base + "..." + head), cwd);
        if (diff.exitCode() != 0) {
            return List.of();
        }
        List<ChangedFile> files = new ArrayList<>();
        for (String line : diff.text().split("\\R")) {
            String[] parts = line.split("\\t");
            if (parts.length < 2) {
                continue;
            }
            files.add(new ChangedFile(parts[0].strip(), parts[parts.length - 1].strip()));
        }
        return files;
    }

    /** 트리 브랜치가 바꾼 파일 목록(메인 저장소 기준). 병합하기 **전에** 부른다 — 병합 뒤에는 차이가 사라진다. */
    private List<ChangedFile> changedFilesOfBranch(String repositoryPath, String branch) throws Exception {
        GitResult base = git(List.of("merge-base", branch, "HEAD"), repositoryPath);
        if (base.exitCode() != 0) {
            return List.of();
        }
        return changedFiles(repositoryPath, base.text().strip(), branch);
    }

    private String shortSha(String cwd) throws Exception {
        GitResult result = git(List.of("rev-parse", "--short", "HEAD"), cwd);
        return result.exitCode() == 0 ? result.text().strip() : null;
    }

    /** 특정 ref 의 짧은 sha(트리 브랜치의 마지막 커밋). 없으면 null. */
    private String shortSha(String cwd, String ref) throws Exception {
        GitResult result = git(List.of("rev-parse", "--short", ref), cwd);
        return result.exitCode() == 0 ? result.text().strip() : null;
    }

    private String branchOf(String cwd) throws Exception {
        GitResult result = git(List.of("rev-parse", "--abbrev-ref", "HEAD"), cwd);
        return result.exitCode() == 0 ? result.text().strip() : null;
    }

    private static int countLines(String text) {
        int count = 0;
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static String firstLines(String text, int max) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line.strip());
            }
            if (lines.size() >= max) {
                break;
            }
        }
        return String.join(", ", lines);
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
        CompletableFuture<Void> out = CompletableFuture.runAsync(() -> stdout.set(read(process.getInputStream())),
                StreamPumps.pool());
        CompletableFuture<Void> err = CompletableFuture.runAsync(() -> stderr.set(read(process.getErrorStream())),
                StreamPumps.pool());
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
