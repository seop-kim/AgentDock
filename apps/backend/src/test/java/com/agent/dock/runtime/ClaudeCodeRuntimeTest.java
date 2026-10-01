package com.agent.dock.runtime;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeCodeRuntimeTest {
    private final ClaudeCodeRuntime runtime = new ClaudeCodeRuntime(null);

    private AgentExecutionRequest request(String systemPrompt, String model, String mode, BigDecimal budget) {
        return new AgentExecutionRequest("exec-1", "prompt", "C:\\Temp", systemPrompt, model, mode, budget,
                (chunk, stream) -> { });
    }

    @Test
    void passesPrintFlagWithoutInlinePromptBecausePromptGoesThroughStdin() {
        List<String> args = runtime.buildArgs(request(null, null, null, null));

        assertThat(args).containsExactly("-p", "--output-format", "stream-json", "--verbose");
        assertThat(args).doesNotContain("prompt");
    }

    @Test
    void passesSystemPromptAsAppendedSystemPrompt() {
        List<String> args = runtime.buildArgs(request("마스터 규칙\n\n내 역할", null, null, null));

        assertThat(args).containsSequence("--append-system-prompt", "마스터 규칙\n\n내 역할");
    }

    @Test
    void passesModelAndModeAsCliFlags() {
        List<String> args = runtime.buildArgs(request(null, "opus", "plan", null));

        assertThat(args).containsSequence("--model", "opus");
        assertThat(args).containsSequence("--permission-mode", "plan");
    }

    /**
     * 계약 JSON 은 --json-schema 로 넘기지 않는다. Java 의 Windows 인자 인용 때문에 따옴표가 든
     * JSON 문자열이 CLI 에 온전히 전달되지 않기 때문이다(2026-09-30 실측). 프롬프트로 강제한다.
     */
    @Test
    void passesBudgetLimitAndNeverJsonSchema() {
        List<String> args = runtime.buildArgs(request(null, null, null, new BigDecimal("5")));

        assertThat(args).containsSequence("--max-budget-usd", "5");
        assertThat(args).doesNotContain("--json-schema");
    }

    @Test
    void omitsFlagsWhenValuesAreMissing() {
        List<String> args = runtime.buildArgs(request(null, null, null, null));

        assertThat(args).doesNotContain("--model", "--permission-mode", "--append-system-prompt", "--max-budget-usd");
    }

    @Test
    void omitsFlagsWhenValuesAreBlank() {
        List<String> args = runtime.buildArgs(request("   ", " ", "", null));

        assertThat(args).doesNotContain("--model", "--permission-mode", "--append-system-prompt", "--max-budget-usd");
    }
}
