package com.agent.dock.runtime.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 CLI 출력(2026-09-30 이 머신에서 확인한 모양)을 그대로 넣어 파서를 고정한다.
 */
class ClaudeStreamJsonTest {

    @Test
    void rendersThinkingTextAndToolUse() {
        String line = """
                {"type":"assistant","message":{"content":[
                  {"type":"thinking","thinking":"팀을 먼저 봐야겠다"},
                  {"type":"text","text":"백엔드 팀에 맡기겠습니다"},
                  {"type":"tool_use","name":"Read","input":{"file_path":"C:\\\\repo\\\\src\\\\Order.java"}}
                ]},"session_id":"s-1"}""";

        List<ClaudeStreamJson.LogLine> lines = ClaudeStreamJson.render(line);

        assertThat(lines).extracting(ClaudeStreamJson.LogLine::stream).containsOnly("stdout");
        assertThat(lines).extracting(ClaudeStreamJson.LogLine::content).containsExactly(
                "✻ 팀을 먼저 봐야겠다\n",
                "백엔드 팀에 맡기겠습니다\n",
                "⏺ Read C:\\repo\\src\\Order.java\n");
    }

    @Test
    void rendersToolResultFirstLine() {
        String line = """
                {"type":"user","message":{"content":[{"type":"tool_result","content":"첫 줄입니다\\n둘째 줄"}]}}""";

        assertThat(ClaudeStreamJson.render(line)).extracting(ClaudeStreamJson.LogLine::content)
                .containsExactly("  ⎿ 첫 줄입니다\n");
    }

    @Test
    void rendersInitAsSystemLineAndSkipsHookNoise() {
        String init = """
                {"type":"system","subtype":"init","model":"claude-opus-4","cwd":"C:\\\\repo"}""";
        String hook = """
                {"type":"system","subtype":"hook_response","output":"...약 2만 토큰 분량..."}""";

        assertThat(ClaudeStreamJson.render(init)).extracting(ClaudeStreamJson.LogLine::stream).containsExactly("system");
        assertThat(ClaudeStreamJson.render(init)).extracting(ClaudeStreamJson.LogLine::content)
                .containsExactly("⎿ 실행 시작 (model=claude-opus-4, cwd=C:\\repo)\n");
        assertThat(ClaudeStreamJson.render(hook)).isEmpty();
    }

    @Test
    void nonJsonLineIsKeptAsIs() {
        assertThat(ClaudeStreamJson.render("Warning: something odd")).extracting(ClaudeStreamJson.LogLine::content)
                .containsExactly("Warning: something odd\n");
    }

    @Test
    void readsResultEventWithStructuredOutputAndMetrics() {
        String line = """
                {"type":"result","subtype":"success","is_error":false,"duration_ms":8103,"num_turns":2,
                 "total_cost_usd":0.269163,"session_id":"38e0910c-4e31-4d46-b08a-1004895acdc0",
                 "result":"{\\"action\\":\\"done\\",\\"summary\\":\\"ok\\"}",
                 "structured_output":{"action":"done","summary":"ok"},
                 "usage":{"input_tokens":2,"output_tokens":323,"cache_read_input_tokens":0,
                          "cache_creation_input_tokens":44052}}""";

        ClaudeStreamJson.Outcome outcome = ClaudeStreamJson.outcome(line).orElseThrow();

        assertThat(outcome.resultText()).isEqualTo("{\"action\":\"done\",\"summary\":\"ok\"}");
        assertThat(outcome.structured()).containsEntry("action", "done").containsEntry("summary", "ok");
        assertThat(outcome.isError()).isFalse();
        assertThat(outcome.metrics().inputTokens()).isEqualTo(2);
        assertThat(outcome.metrics().outputTokens()).isEqualTo(323);
        assertThat(outcome.metrics().cacheReadTokens()).isZero();
        assertThat(outcome.metrics().cacheCreationTokens()).isEqualTo(44052);
        assertThat(outcome.metrics().costUsd()).isEqualByComparingTo(new BigDecimal("0.269163"));
        assertThat(outcome.metrics().durationMs()).isEqualTo(8103);
        assertThat(outcome.metrics().numTurns()).isEqualTo(2);
        assertThat(outcome.metrics().sessionId()).isEqualTo("38e0910c-4e31-4d46-b08a-1004895acdc0");
    }

    @Test
    void parsesStructuredOutputFromResultTextWhenStructuredFieldIsMissing() {
        String line = """
                {"type":"result","is_error":false,"result":"{\\"action\\":\\"delegate\\",\\"target\\":\\"Backend Team\\"}",
                 "usage":{"input_tokens":1,"output_tokens":2}}""";

        ClaudeStreamJson.Outcome outcome = ClaudeStreamJson.outcome(line).orElseThrow();

        assertThat(outcome.structured()).containsEntry("action", "delegate").containsEntry("target", "Backend Team");
    }

    @Test
    void parsesJsonObjectWrappedInCodeFenceOrProse() {
        String fenced = """
                {"type":"result","is_error":false,"result":"```json\\n{\\"action\\":\\"done\\",\\"summary\\":\\"ok\\"}\\n```",
                 "usage":{"input_tokens":1}}""";
        String prose = """
                {"type":"result","is_error":false,"result":"결과입니다: {\\"action\\":\\"done\\",\\"summary\\":\\"ok\\"} 이상입니다",
                 "usage":{"input_tokens":1}}""";

        assertThat(ClaudeStreamJson.outcome(fenced).orElseThrow().structured()).containsEntry("action", "done");
        assertThat(ClaudeStreamJson.outcome(prose).orElseThrow().structured()).containsEntry("action", "done");
    }

    @Test
    void plainTextResultHasNoStructuredPayload() {
        String line = """
                {"type":"result","is_error":false,"result":"그냥 텍스트","usage":{"input_tokens":1}}""";

        ClaudeStreamJson.Outcome outcome = ClaudeStreamJson.outcome(line).orElseThrow();

        assertThat(outcome.resultText()).isEqualTo("그냥 텍스트");
        assertThat(outcome.structured()).isNull();
        assertThat(outcome.metrics().costUsd()).isNull();
    }

    @Test
    void outcomeIsEmptyForOtherEvents() {
        assertThat(ClaudeStreamJson.outcome("{\"type\":\"assistant\"}")).isEmpty();
        assertThat(ClaudeStreamJson.outcome("not json")).isEmpty();
    }
}
