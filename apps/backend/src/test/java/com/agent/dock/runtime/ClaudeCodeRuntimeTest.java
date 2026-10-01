package com.agent.dock.runtime;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeCodeRuntimeTest {
    private final ClaudeCodeRuntime runtime = new ClaudeCodeRuntime(null);

    private AgentExecutionRequest request(String systemPrompt, String model, String mode, String contract, BigDecimal budget) {
        return new AgentExecutionRequest("exec-1", "prompt", "C:\\Temp", systemPrompt, model, mode, contract, budget,
                (chunk, stream) -> { });
    }

    @Test
    void alwaysPassesPromptAndStreamJson() {
        List<String> args = runtime.buildArgs(request(null, null, null, null, null));

        assertThat(args).containsExactly("-p", "prompt", "--output-format", "stream-json", "--verbose");
    }

    @Test
    void passesSystemPromptAsAppendedSystemPrompt() {
        List<String> args = runtime.buildArgs(request("마스터 규칙\n\n내 역할", null, null, null, null));

        assertThat(args).containsSequence("--append-system-prompt", "마스터 규칙\n\n내 역할");
    }

    @Test
    void passesModelAndModeAsCliFlags() {
        List<String> args = runtime.buildArgs(request(null, "opus", "plan", null, null));

        assertThat(args).containsSequence("--model", "opus");
        assertThat(args).containsSequence("--permission-mode", "plan");
    }

    @Test
    void passesContractSchemaAndBudgetForJudgementSteps() {
        String schema = "{\"type\":\"object\"}";

        List<String> args = runtime.buildArgs(request(null, null, null, schema, new BigDecimal("5")));

        assertThat(args).containsSequence("--json-schema", schema);
        assertThat(args).containsSequence("--max-budget-usd", "5");
    }

    @Test
    void omitsFlagsWhenValuesAreMissing() {
        List<String> args = runtime.buildArgs(request(null, null, null, null, null));

        assertThat(args).doesNotContain("--model", "--permission-mode", "--append-system-prompt", "--json-schema",
                "--max-budget-usd");
    }

    @Test
    void omitsFlagsWhenValuesAreBlank() {
        List<String> args = runtime.buildArgs(request("   ", " ", "", "  ", null));

        assertThat(args).doesNotContain("--model", "--permission-mode", "--append-system-prompt", "--json-schema",
                "--max-budget-usd");
    }
}
