package com.agent.dock.execution.service;

import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.execution.domain.ChangedFile;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.dto.ExecutionDiffResponse;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.process.service.ProcessService;
import com.agent.dock.process.util.Executables;
import com.agent.dock.workspace.domain.Workspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

/**
 * 트리 루트의 changed_files 를 파일별 unified diff 로 만든다. git 은 ProcessService 로 셸 미경유 실행하고,
 * 루트에 기록이 없으면 available=false 와 사유를 돌려준다.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionDiffServiceTest {

    @Mock ExecutionRepository executionRepository;
    private ExecutionDiffService service;

    @BeforeEach
    void setUp() {
        service = new ExecutionDiffService(executionRepository, new ProcessService());
    }
    @Test
    void producesPerFileUnifiedDiffFromTheRootCommit(@TempDir Path tempDir) throws Exception {
        Path repo = gitRepo(tempDir);
        String sha = commitChange(repo, "old\n", "new\n");

        Execution root = execution(7L, null, List.of(new ChangedFile("M", "a.txt")), sha, repo);
        when(executionRepository.findById(7L)).thenReturn(Optional.of(root));

        ExecutionDiffResponse response = service.diff(7L).join();

        assertThat(response.available()).isTrue();
        assertThat(response.executionId()).isEqualTo(7L);
        assertThat(response.files()).hasSize(1);
        assertThat(response.files().get(0).path()).isEqualTo("a.txt");
        assertThat(response.files().get(0).patch()).contains("-old").contains("+new");
    }

    @Test
    void resolvesTheTreeRootFromAChildExecution(@TempDir Path tempDir) throws Exception {
        Path repo = gitRepo(tempDir);
        String sha = commitChange(repo, "one\n", "two\n");

        Execution child = new Execution();
        child.setId(20L);
        child.setRootExecutionId(10L);
        Execution root = execution(10L, null, List.of(new ChangedFile("M", "a.txt")), sha, repo);
        when(executionRepository.findById(20L)).thenReturn(Optional.of(child));
        when(executionRepository.findById(10L)).thenReturn(Optional.of(root));

        ExecutionDiffResponse response = service.diff(20L).join();

        assertThat(response.executionId()).isEqualTo(20L);
        assertThat(response.rootExecutionId()).isEqualTo(10L);
        assertThat(response.available()).isTrue();
        assertThat(response.files().get(0).patch()).contains("+two");
    }

    @Test
    void buildsDiffFromTheWorkspaceAfterTheWorktreeWasCleanedUp(@TempDir Path tempDir) throws Exception {
        Path repo = gitRepo(tempDir);
        String sha = commitChange(repo, "before\n", "after\n");

        Execution root = execution(9L, null, List.of(new ChangedFile("M", "a.txt")), sha, repo);
        root.setWorktreePath(null); // 병합 정책으로 워크트리가 정리된 뒤 — 커밋은 메인 저장소에 남아 diff 가 계속 동작한다
        when(executionRepository.findById(9L)).thenReturn(Optional.of(root));

        ExecutionDiffResponse response = service.diff(9L).join();

        assertThat(response.available()).isTrue();
        assertThat(response.files().get(0).patch()).contains("+after");
    }

    private static Execution execution(Long id, Long rootId, List<ChangedFile> files, String commit, Path repo) {
        Execution execution = new Execution();
        execution.setId(id);
        execution.setRootExecutionId(rootId);
        execution.setChangedFiles(files);
        execution.setResultCommit(commit);
        Workspace workspace = new Workspace();
        workspace.setPath(repo.toString());
        execution.setWorkspace(workspace);
        return execution;
    }
    @Test
    void reportsUnavailableWhenRootHasNoChangedFiles() {
        Execution root = new Execution();
        root.setId(5L);
        when(executionRepository.findById(5L)).thenReturn(Optional.of(root));

        ExecutionDiffResponse response = service.diff(5L).join();

        assertThat(response.available()).isFalse();
        assertThat(response.reason()).contains("변경 파일");
        assertThat(response.files()).isEmpty();
    }

    @Test
    void reportsUnavailableWhenRootHasNoCommit() {
        Execution root = new Execution();
        root.setId(6L);
        root.setChangedFiles(List.of(new ChangedFile("M", "a.txt")));
        when(executionRepository.findById(6L)).thenReturn(Optional.of(root));

        ExecutionDiffResponse response = service.diff(6L).join();

        assertThat(response.available()).isFalse();
        assertThat(response.reason()).contains("커밋");
    }

    @Test
    void missingExecutionIsNotFound() {
        when(executionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.diff(1L)).isInstanceOf(NotFoundException.class);
    }
    private static Path gitRepo(Path tempDir) throws Exception {
        String git = Executables.locate("git");
        assumeTrue(git != null, "git 을 쓸 수 없어 건너뜁니다");
        Path repo = Files.createDirectories(tempDir.resolve("repo"));
        assumeTrue(git(git, repo, "init").exitCode() == 0, "git init 실패로 건너뜁니다");
        git(git, repo, "config", "user.email", "test@example.com");
        git(git, repo, "config", "user.name", "test");
        git(git, repo, "config", "commit.gpgsign", "false");
        return repo;
    }

    private static String commitChange(Path repo, String before, String after) throws Exception {
        String git = Executables.locate("git");
        Files.writeString(repo.resolve("a.txt"), before);
        git(git, repo, "add", ".");
        assumeTrue(git(git, repo, "commit", "-m", "base").exitCode() == 0, "git commit 실패로 건너뜁니다");
        Files.writeString(repo.resolve("a.txt"), after);
        git(git, repo, "add", ".");
        assumeTrue(git(git, repo, "commit", "-m", "change").exitCode() == 0, "git commit 실패로 건너뜁니다");
        return git(git, repo, "rev-parse", "--short", "HEAD").text().strip();
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