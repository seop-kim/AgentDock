package com.agent.dock.delegation.service;

import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.service.ExecutionFactory;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.service.ExecutionGuard;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.service.ExecutionRunner;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.service.ExecutionService;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.service.ExecutionStreamHub;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.service.WorktreeService;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.group.service.GroupService;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.project.repository.ProjectRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * 위임 지시·문맥을 프롬프트에 인라인하지 않고 작업 디렉터리(cwd) 안 `.agentdock/prompts` 파일로 넘기는 동작.
 * 파일이 실제로 쓰이고(상대 경로 반환), 쓸 수 없으면 인라인 폴백으로 되돌아가는지(실행을 막지 않는지)를 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class DelegationServiceTest {

    @Mock ExecutionService executionService;
    @Mock ExecutionRunner runner;
    @Mock ExecutionStreamHub streamHub;
    @Mock ExecutionFactory executionFactory;
    @Mock ExecutionRepository executionRepository;
    @Mock ExecutionGuard guard;
    @Mock AgentRepository agentRepository;
    @Mock ProjectRepository projectRepository;
    @Mock GroupService groupService;
    @Mock RuleRouter ruleRouter;
    @Mock WorktreeService worktreeService;
    @Mock EventPublisher changeEvents;
    @InjectMocks DelegationService service;

    @TempDir Path cwd;

    @Test
    void writesTheInstructionFileAndReturnsItsRelativePath() throws Exception {
        String content = "## 원래 요청\n\n파일을 만들어 주세요\n";

        String reference = service.writePromptFileLogged(42L, cwd.toString(), "42.md", content);

        assertThat(reference).isEqualTo(".agentdock/prompts/42.md");
        assertThat(Files.readString(cwd.resolve(".agentdock/prompts/42.md"))).isEqualTo(content);
        verify(streamHub).system(42L, "⎿ 지시 파일: .agentdock/prompts/42.md (%,d자)".formatted(content.length()));
    }

    @Test
    void writesTheStepContextFileWithTheStepName() throws Exception {
        String reference = service.writePromptFileLogged(50L, cwd.toString(), "50-step2.md", "문맥");

        assertThat(reference).isEqualTo(".agentdock/prompts/50-step2.md");
        assertThat(Files.exists(cwd.resolve(".agentdock/prompts/50-step2.md"))).isTrue();
    }

    @Test
    void fallsBackWhenTheFileCannotBeWritten() throws Exception {
        // cwd 자리에 파일을 주면 그 아래에 폴더를 만들 수 없다(권한·경로 오류 상황을 재현).
        Path notADirectory = Files.createFile(cwd.resolve("not-a-directory"));

        String reference = service.writePromptFileLogged(42L, notADirectory.toString(), "42.md", "본문");

        assertThat(reference).isNull();
        verify(streamHub).system(eq(42L), contains("지시 파일을 쓰지 못했습니다"));
    }
}
