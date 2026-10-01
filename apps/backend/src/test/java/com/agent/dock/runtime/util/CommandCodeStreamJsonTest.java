package com.agent.dock.runtime.util;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 `cmdc -p --output-format json` 출력(2026-10-01 이 머신에서 확인한 모양)을 그대로 넣어 파서를 고정한다.
 */
class CommandCodeStreamJsonTest {

    @Test
    void rendersModelRequestStartAsSystemLineAndSkipsStreamingFragments() {
        String start = """
                {"type":"event","event":{"type":"model_request_start","model":"deepseek/deepseek-v4-flash"}}""";
        String thinkingDelta = """
                {"type":"event","event":{"type":"thinking_delta","delta":"The"}}""";
        String runStart = """
                {"type":"event","event":{"type":"run_start","sessionId":"6462a41f-fe05-48a4-b255-ab4f92c84f4d"}}""";
        String trace = """
                {"type":"event","event":{"type":"model_trace","traceId":"b30dd326ce6b56ee2e046ca677be0b2f"}}""";

        assertThat(CommandCodeStreamJson.render(start)).extracting(CommandCodeStreamJson.LogLine::stream)
                .containsExactly("system");
        assertThat(CommandCodeStreamJson.render(start)).extracting(CommandCodeStreamJson.LogLine::content)
                .containsExactly("⎿ 실행 시작 (model=deepseek/deepseek-v4-flash)\n");
        assertThat(CommandCodeStreamJson.render(thinkingDelta)).isEmpty();
        assertThat(CommandCodeStreamJson.render(runStart)).isEmpty();
        assertThat(CommandCodeStreamJson.render(trace)).isEmpty();
    }

    @Test
    void rendersThinkingEndAndMessageTextBlocksWithoutDuplicatingThinking() {
        String thinkingEnd = """
                {"type":"event","event":{"type":"thinking_end","text":"The user asks \\"1+1\\" in Korean. Simple answer."}}""";
        String messageEnd = """
                {"type":"event","event":{"type":"message_end","content":[{"type":"thinking","thinking":"The user asks \\"1+1\\" in Korean. Simple answer.","signature":""},{"type":"text","text":"2"}]}}""";

        assertThat(CommandCodeStreamJson.render(thinkingEnd)).extracting(CommandCodeStreamJson.LogLine::content)
                .containsExactly("✻ The user asks \"1+1\" in Korean. Simple answer.\n");
        // thinking 은 thinking_end 에서만 남긴다(중복 방지). message_end 에서는 text 블록만 로그로 쓴다.
        assertThat(CommandCodeStreamJson.render(messageEnd)).extracting(CommandCodeStreamJson.LogLine::content)
                .containsExactly("2\n");
    }

    @Test
    void nonJsonLineIsKeptAsIs() {
        assertThat(CommandCodeStreamJson.render("Warning: something odd"))
                .extracting(CommandCodeStreamJson.LogLine::content)
                .containsExactly("Warning: something odd\n");
    }

    @Test
    void readsResultLineWithFinalTextAndMetricsAndNoCost() {
        String line = """
                {"type":"result","subtype":"success","sessionId":"6462a41f-fe05-48a4-b255-ab4f92c84f4d","stopReason":"end_turn","usage":{"inputTokens":16249,"outputTokens":16,"cacheReadTokens":5248,"cacheWriteTokens":0},"durationMs":4988,"finalText":"2"}""";

        CommandCodeStreamJson.Outcome outcome = CommandCodeStreamJson.outcome(line).orElseThrow();

        assertThat(outcome.resultText()).isEqualTo("2");
        assertThat(outcome.structured()).isNull();
        assertThat(outcome.isError()).isFalse();
        assertThat(outcome.metrics().inputTokens()).isEqualTo(16249);
        assertThat(outcome.metrics().outputTokens()).isEqualTo(16);
        assertThat(outcome.metrics().cacheReadTokens()).isEqualTo(5248);
        assertThat(outcome.metrics().cacheCreationTokens()).isZero();
        assertThat(outcome.metrics().costUsd()).isNull();
        assertThat(outcome.metrics().durationMs()).isEqualTo(4988);
        assertThat(outcome.metrics().numTurns()).isNull();
        assertThat(outcome.metrics().sessionId()).isEqualTo("6462a41f-fe05-48a4-b255-ab4f92c84f4d");

        assertThat(CommandCodeStreamJson.render(line)).extracting(CommandCodeStreamJson.LogLine::content)
                .containsExactly("2\n");
    }

    @Test
    void parsesJsonObjectInsideFinalTextAsStructuredPayload() {
        String line = """
                {"type":"result","subtype":"success","finalText":"결과입니다: {\\"action\\":\\"done\\",\\"summary\\":\\"ok\\"}","usage":{"inputTokens":1,"outputTokens":2}}""";

        assertThat(CommandCodeStreamJson.outcome(line).orElseThrow().structured())
                .containsEntry("action", "done").containsEntry("summary", "ok");
    }

    @Test
    void nonSuccessSubtypeIsErrorAndOtherLinesHaveNoOutcome() {
        String line = """
                {"type":"result","subtype":"error_max_turns","finalText":"중단","usage":{}}""";

        assertThat(CommandCodeStreamJson.outcome(line).orElseThrow().isError()).isTrue();
        assertThat(CommandCodeStreamJson.outcome("{\"type\":\"event\"}")).isEmpty();
        assertThat(CommandCodeStreamJson.outcome("not json")).isEmpty();
    }
}
