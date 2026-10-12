package com.agent.dock.execution.service;

import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.execution.domain.ChangedFile;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.dto.ExecutionDiffResponse;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.process.service.ProcessService;
import com.agent.dock.runtime.service.StreamPumps;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * 실행 트리 루트가 바꾼 파일별 unified diff 를 만든다(GET /executions/{id}/diff).
 *
 * <p>무엇을 보여 줄지는 루트 실행의 changed_files(git diff --name-status 로 남긴 것)가 정하고, 각 파일의
 * 내용은 루트가 남긴 커밋(result_commit)에서 꺼낸다. 이렇게 하면 메인 저장소로 병합한 뒤에도 커밋이
 * 남아 있어 diff 를 다시 볼 수 있다. git 은 ProcessService 로 셸 없이 실행하고, 무거운 git 실행은
 * workers 풀(StreamPumps)에서 돌려 요청 스레드를 막지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionDiffService {

    /** 파일 하나의 patch 최대 길이. 넘으면 잘라 내고 truncated=true 로 알린다(터미널 diff 가 매우 클 수 있다). */
    static final int MAX_PATCH_CHARS = 200_000;

    private final ExecutionRepository executionRepository;
    private final ProcessService processService;

    /**
     * 트리 루트의 changed_files 를 파일별 unified diff 로 만든다. DB 조회는 요청 스레드에서 끝내고
     * git 실행만 workers 풀에 맡겨 CompletableFuture 로 돌려준다.
     */
    @Transactional(readOnly = true)
    public CompletableFuture<ExecutionDiffResponse> diff(Long executionId) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new NotFoundException("Execution %d not found".formatted(executionId)));
        Long rootId = execution.getRootExecutionId() == null ? execution.getId() : execution.getRootExecutionId();
        Execution root = rootId.equals(execution.getId()) ? execution
                : executionRepository.findById(rootId).orElse(execution);
        List<ChangedFile> changed = root.getChangedFiles();
        if (changed == null || changed.isEmpty()) {
            return CompletableFuture.completedFuture(ExecutionDiffResponse.unavailable(executionId, root.getId(),
                    "트리 루트에 변경 파일 기록이 없습니다(격리되지 않았거나 커밋할 변경이 없었습니다)"));
        }
        String commit = root.getResultCommit();
        if (commit == null || commit.isBlank()) {
            return CompletableFuture.completedFuture(ExecutionDiffResponse.unavailable(executionId, root.getId(),
                    "트리 루트의 커밋이 없어 diff 를 만들 수 없습니다"));
        }
        String cwd = resolveCwd(root);
        if (cwd == null) {
            return CompletableFuture.completedFuture(ExecutionDiffResponse.unavailable(executionId, root.getId(),
                    "워크트리나 워크스페이스 폴더를 찾을 수 없습니다"));
        }
        List<ChangedFile> files = List.copyOf(changed);
        Long rootExecutionId = root.getId();
        return CompletableFuture.supplyAsync(
                () -> build(executionId, rootExecutionId, cwd, commit, files), StreamPumps.pool());
    }

    /** diff 를 만들 작업 디렉터리: 살아 있는 워크트리 → 워크스페이스(메인 저장소) 순. 둘 다 없으면 null. */
    private String resolveCwd(Execution root) {
        String worktree = root.getWorktreePath();
        if (isDirectory(worktree)) {
            return worktree;
        }
        if (root.getWorkspace() != null && isDirectory(root.getWorkspace().getPath())) {
            return root.getWorkspace().getPath();
        }
        return null;
    }

    private static boolean isDirectory(String path) {
        return path != null && !path.isBlank() && new File(path).isDirectory();
    }
    private ExecutionDiffResponse build(Long executionId, Long rootExecutionId, String cwd, String commit,
                                        List<ChangedFile> files) {
        List<ExecutionDiffResponse.FileDiff> diffs = new ArrayList<>();
        for (ChangedFile file : files) {
            diffs.add(fileDiff(cwd, commit, file));
        }
        return new ExecutionDiffResponse(executionId, rootExecutionId, true, null, diffs);
    }

    /** 파일 하나의 unified diff. git 이 실패하거나 결과가 비면 patch=null 로 실어 화면이 "표시할 diff 없음"을 그린다. */
    private ExecutionDiffResponse.FileDiff fileDiff(String cwd, String commit, ChangedFile file) {
        try {
            GitResult result = git(List.of("show", "--no-color", "--patch", "--pretty=format:",
                    commit, "--", file.path()), cwd);
            if (result.exitCode() != 0 || result.text().isBlank()) {
                return new ExecutionDiffResponse.FileDiff(file.status(), file.path(), null, false);
            }
            String patch = result.text();
            boolean truncated = patch.length() > MAX_PATCH_CHARS;
            if (truncated) {
                patch = patch.substring(0, MAX_PATCH_CHARS) + "\n… (이하 생략: diff 가 너무 큽니다)";
            }
            return new ExecutionDiffResponse.FileDiff(file.status(), file.path(), patch, truncated);
        } catch (Exception ex) {
            log.warn("diff 생성 실패 commit={} path={}: {}", commit, file.path(), ex.getMessage());
            return new ExecutionDiffResponse.FileDiff(file.status(), file.path(), null, false);
        }
    }
    private record GitResult(int exitCode, String text) {
    }

    /** git 한 번. 셸을 쓰지 않고 stdout/stderr 를 함께 모아 돌려준다(짧은 명령이라 교착이 없다). */
    private GitResult git(List<String> args, String cwd) throws Exception {
        Process process = processService.spawn("diff-" + UUID.randomUUID(), "git", args, cwd);
        AtomicReference<String> stdout = new AtomicReference<>("");
        AtomicReference<String> stderr = new AtomicReference<>("");
        CompletableFuture<Void> out = CompletableFuture.runAsync(
                () -> stdout.set(read(process.getInputStream())), StreamPumps.pool());
        CompletableFuture<Void> err = CompletableFuture.runAsync(
                () -> stderr.set(read(process.getErrorStream())), StreamPumps.pool());
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
}