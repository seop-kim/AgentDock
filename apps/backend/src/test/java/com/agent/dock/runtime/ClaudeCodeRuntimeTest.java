package com.agent.dock.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeCodeRuntimeTest {
    private final ClaudeCodeRuntime runtime = new ClaudeCodeRuntime(null);

    private AgentExecutionRequest request(String model, String mode) {
        return new AgentExecutionRequest("exec-1", "prompt", "C:\\Temp", model, mode, (chunk, stream) -> { });
    }

    @Test
    void alwaysPassesPromptAndOutputFormat() {
        List<String> args = runtime.buildArgs(request(null, null));

        assertThat(args).containsExactly("-p", "prompt", "--output-format", "text");
    }

    @Test
    void passesModelAndModeAsCliFlags() {
        List<String> args = runtime.buildArgs(request("opus", "plan"));

        assertThat(args).containsSequence("--model", "opus");
        assertThat(args).containsSequence("--permission-mode", "plan");
    }

    @Test
    void omitsFlagsWhenValuesAreMissing() {
        List<String> args = runtime.buildArgs(request(null, null));

        assertThat(args).doesNotContain("--model", "--permission-mode");
    }

    @Test
    void omitsFlagsWhenValuesAreBlank() {
        List<String> args = runtime.buildArgs(request("   ", ""));

        assertThat(args).doesNotContain("--model", "--permission-mode");
    }
}
