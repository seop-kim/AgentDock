package com.agent.dock.provider.service;

import com.agent.dock.provider.dto.ProbeResult;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * probe 의 종료 코드·출력 → ok/failure 매핑을 고정한다.
 * (실제 CLI 호출은 테스트하지 않는다 — 여기서는 해석만 본다.)
 */
class CommandCodeProbeTest {

    @Test
    void successWhenExitZeroAndResultLineHasFinalText() {
        String line = """
                {"type":"result","subtype":"success","sessionId":"s-1","usage":{"inputTokens":1,"outputTokens":2},"durationMs":10,"finalText":"OK"}""";

        ProbeResult result = CommandCodeProbe.interpret(0, line);

        assertThat(result.ok()).isTrue();
        assertThat(result.cliMissing()).isFalse();
        assertThat(result.detail()).isEqualTo("OK");
    }

    @Test
    void successWithOkWhenOutputIsEmptyButExitZero() {
        ProbeResult result = CommandCodeProbe.interpret(0, "");

        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).isEqualTo("OK");
    }

    @Test
    void failureWhenExitCodeIsNonZero() {
        ProbeResult result = CommandCodeProbe.interpret(1, "boom");

        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).isEqualTo("boom");
    }

    @Test
    void failureMentionsExitCodeWhenOutputIsEmpty() {
        ProbeResult result = CommandCodeProbe.interpret(1, "");

        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("종료 코드 1");
    }

    @Test
    void failurePrefersMessageFromResultLine() {
        String line = """
                {"type":"result","subtype":"error","finalText":"not logged in","usage":{}}""";

        ProbeResult result = CommandCodeProbe.interpret(1, line);

        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).isEqualTo("not logged in");
    }

    @Test
    void shortensAndCollapsesLongDetail() {
        String noisy = "line one\nline two\nline three";

        ProbeResult result = CommandCodeProbe.interpret(0, noisy);

        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).isEqualTo("line one line two line three");
    }
}
