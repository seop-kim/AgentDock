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
}
