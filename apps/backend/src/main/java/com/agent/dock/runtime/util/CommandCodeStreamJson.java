package com.agent.dock.runtime.util;

import com.agent.dock.runtime.dto.ExecutionMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Command Code CLI(`cmdc`)의 `-p --output-format json` 한 줄을 로그 줄과 계측값으로 바꾼다.
 *
 * `--output-format json` 은 <b>NDJSON 이벤트 스트림 + 마지막 result 한 줄</b>을 낸다(2026-10-01 이 머신 실측).
 * <pre>
 * {"type":"event","event":{"type":"run_start","sessionId":"..."}}
 * {"type":"event","event":{"type":"thinking_delta","delta":"The"}}          // 조각 → 버린다
 * {"type":"event","event":{"type":"thinking_end","text":"...전체 사고..."}}
 * {"type":"event","event":{"type":"text_delta","delta":"2"}}                // 조각 → 버린다
 * {"type":"event","event":{"type":"message_end","content":[{"type":"thinking",...},{"type":"text","text":"2"}]}}
 * {"type":"result","subtype":"success","sessionId":"...","stopReason":"end_turn",
 *  "usage":{"inputTokens":16249,"outputTokens":16,"cacheReadTokens":5248,"cacheWriteTokens":0},
 *  "durationMs":4988,"finalText":"2"}
 * </pre>
 *
 * 로그로 남기는 것: `model_request_start`(실행 시작 system 줄), `thinking_end`(사고 전체),
 * `message_end` 의 text 블록. 조각(delta)·중복(message_update·run_end)·내부 이벤트(model_trace 등)는 버린다.
 * result 줄의 `finalText` 에 비용 필드가 없으므로 `costUsd` 는 null 로 둔다(추정하지 않는다).
 */
public final class CommandCodeStreamJson {

    private CommandCodeStreamJson() {
    }

    public record LogLine(String stream, String content) {
    }

    /** result 줄에서 뽑은 계측값과 결과(프로세스 종료 코드는 런타임이 붙인다). */
    public record Outcome(String resultText, Map<String, Object> structured, ExecutionMetrics metrics, boolean isError) {
    }

    /** 한 줄을 로그로 바꾼다. JSON 이 아니면 원문을 남기고, 로그로 쓰지 않는 줄은 버린다. */
    public static List<LogLine> render(String line) {
        Optional<JsonNode> parsed = JsonObjects.parse(line);
        if (parsed.isEmpty()) {
            return List.of(new LogLine("stdout", line + "\n"));
        }
        JsonNode record = parsed.get();
        return switch (record.path("type").asText("")) {
            case "event" -> renderEvent(record.path("event"));
            case "result" -> renderResult(record);
            default -> List.of();
        };
    }

    /** result 줄이면 계측값/결과를 돌려준다. 그 밖의 줄은 비어 있다. */
    public static Optional<Outcome> outcome(String line) {
        Optional<JsonNode> parsed = JsonObjects.parse(line);
        if (parsed.isEmpty() || !"result".equals(parsed.get().path("type").asText(""))) {
            return Optional.empty();
        }
        JsonNode record = parsed.get();
        String finalText = record.path("finalText").asText("");
        return Optional.of(new Outcome(
                finalText.isBlank() ? null : finalText,
                JsonObjects.objectIn(finalText),
                metricsOf(record),
                !"success".equals(record.path("subtype").asText(""))));
    }

    private static List<LogLine> renderEvent(JsonNode event) {
        List<LogLine> lines = new ArrayList<>();
        switch (event.path("type").asText("")) {
            case "model_request_start" -> lines.add(new LogLine("system",
                    "⎿ 실행 시작 (model=%s)\n".formatted(event.path("model").asText("기본"))));
            case "thinking_end" -> appendText(event.path("text").asText(""), "✻ ", lines);
            // message_end 의 content 에는 thinking 과 text 가 함께 온다. 사고는 thinking_end 에서만
            // 남기고(중복 방지) 여기서는 text 블록만 로그로 쓴다.
            case "message_end" -> appendTextBlocks(event.path("content"), lines);
            default -> {
                // run_start/turn_start/message_start/model_trace/thinking_*/text_delta/
                // message_update/model_request_end/turn_end/run_end 등은 조각이거나 중복이라 버린다.
            }
        }
        return lines;
    }

    private static List<LogLine> renderResult(JsonNode record) {
        String finalText = record.path("finalText").asText("");
        if (finalText.isBlank()) {
            return List.of();
        }
        return List.of(new LogLine("stdout", finalText + "\n"));
    }

    private static void appendTextBlocks(JsonNode content, List<LogLine> lines) {
        if (!content.isArray()) {
            return;
        }
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText(""))) {
                appendText(block.path("text").asText(""), "", lines);
            }
        }
    }

    private static void appendText(String text, String prefix, List<LogLine> lines) {
        if (!text.isBlank()) {
            lines.add(new LogLine("stdout", prefix + text + "\n"));
        }
    }

    private static ExecutionMetrics metricsOf(JsonNode record) {
        JsonNode usage = record.path("usage");
        return new ExecutionMetrics(
                intOrNull(usage, "inputTokens"),
                intOrNull(usage, "outputTokens"),
                intOrNull(usage, "cacheReadTokens"),
                intOrNull(usage, "cacheWriteTokens"),
                // cmdc 의 result 줄에는 비용 필드가 없다(2026-10-01 실측). 추정하지 않고 null 로 둔다.
                null,
                record.path("durationMs").isNumber() ? record.path("durationMs").asLong() : null,
                // 턴 수는 result 줄에 없다(run_end 에만 있고 그 줄은 계측에 쓰지 않는다).
                null,
                record.path("sessionId").isTextual() ? record.path("sessionId").asText() : null);
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asInt() : null;
    }
}
