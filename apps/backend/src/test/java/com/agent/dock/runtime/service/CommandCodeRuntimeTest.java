package com.agent.dock.runtime.service;

import com.agent.dock.runtime.dto.AgentExecutionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CommandCodeRuntimeTest {
    private final CommandCodeRuntime runtime = new CommandCodeRuntime(null);

    private AgentExecutionRequest request(String model, String mode) {
        return new AgentExecutionRequest("exec-1", "prompt", "C:\\Temp", null, model, mode, null,
                (chunk, stream) -> { });
    }

    @Test
    void passesJsonOutputFormatWithoutInlinePromptBecausePromptGoesThroughStdin() {
        List<String> args = runtime.buildArgs(request(null, null));

        assertThat(args).containsExactly("-p", "--output-format", "json", "-t");
        assertThat(args).doesNotContain("prompt");
    }

    /**
     * `-t`(프로젝트 자동 신뢰)는 항상 붙인다 — 무인 실행이 첫 권한 프롬프트에서 멈추지 않게.
     */
    @Test
    void alwaysTrustsProjectSoUnattendedRunDoesNotWaitForPermissionPrompt() {
        assertThat(runtime.buildArgs(request(null, null))).contains("-t");
        assertThat(runtime.buildArgs(request("moonshotai/kimi-k2.5", "plan"))).contains("-t");
    }

    @Test
    void passesModelAndModeAsCliFlags() {
        List<String> args = runtime.buildArgs(request("moonshotai/kimi-k2.5", "accept-edits"));

        assertThat(args).containsSequence("--model", "moonshotai/kimi-k2.5");
        assertThat(args).containsSequence("--permission-mode", "accept-edits");
    }

    @Test
    void omitsFlagsWhenValuesAreMissing() {
        assertThat(runtime.buildArgs(request(null, null)))
                .doesNotContain("--model", "--permission-mode");
    }

    @Test
    void omitsFlagsWhenValuesAreBlank() {
        assertThat(runtime.buildArgs(request("   ", "")))
                .doesNotContain("--model", "--permission-mode");
    }

    /** 같은 실행의 다음 스텝은 자기 세션을 이어받는다. 자식은 분기 플래그가 없어 새 세션으로 시작한다. */
    @Test
    void resumesItsOwnSessionButStartsChildrenFresh() {
        assertThat(runtime.buildArgs(request(null, null))).doesNotContain("--resume");

        AgentExecutionRequest resumed = new AgentExecutionRequest("exec-1", "prompt", "C:\\Temp", null, null, null,
                null, (chunk, stream) -> { }, "session-1", false);
        assertThat(runtime.buildArgs(resumed)).containsSequence("--resume", "session-1");

        // cmdc 에는 --fork-session 이 확인되지 않는다(추측 금지) — 자식은 새 세션으로 시작한다.
        AgentExecutionRequest child = new AgentExecutionRequest("exec-1", "prompt", "C:\\Temp", null, null, null,
                null, (chunk, stream) -> { }, "session-1", true);
        assertThat(runtime.buildArgs(child)).doesNotContain("--resume");
    }
}
