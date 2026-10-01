package com.agent.dock.runtime;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Claude Code CLI 의 `--output-format stream-json` 한 줄을 로그 줄과 계측값으로 바꾼다.
 *
 * 실제 출력으로 확인한 이벤트 모양(2026-09-30 이 머신에서 실측):
 * <pre>
 * {"type":"system","subtype":"init", ...}                       // 시작(모델·cwd). hook_response 는 버린다
 * {"type":"assistant","message":{"content":[{"type":"thinking"|"text"|"tool_use", ...}]}, ...}
 * {"type":"user","message":{"content":[{"type":"tool_result", ...}]}, ...}
 * {"type":"result","result":"{...}","structured_output":{...},"usage":{...},
 *  "total_cost_usd":0.269,"duration_ms":8103,"num_turns":2,"session_id":"...","is_error":false}
 * </pre>
 */
public final class ClaudeStreamJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int SUMMARY_LIMIT = 120;
    private static final String[] SUMMARY_KEYS = {"command", "file_path", "path", "pattern", "url", "query", "prompt"};

    private ClaudeStreamJson() {
    }

    public record LogLine(String stream, String content) {
    }

    /** result 이벤트에서 뽑은 계측값과 결과(프로세스 종료 코드는 런타임이 붙인다). */
    public record Outcome(String resultText, Map<String, Object> structured, ExecutionMetrics metrics, boolean isError) {
    }

    /** 한 줄을 로그로 바꾼다. JSON 이 아니거나 우리가 다루지 않는 이벤트는 원문을 그대로 남긴다. */
    public static List<LogLine> render(String line) {
        Optional<JsonNode> parsed = parse(line);
        if (parsed.isEmpty()) {
            return List.of(new LogLine("stdout", line + "\n"));
        }
        JsonNode event = parsed.get();
        List<LogLine> lines = new ArrayList<>();
        switch (event.path("type").asText("")) {
            case "assistant" -> appendContent(event.path("message").path("content"), lines);
            case "user" -> appendToolResults(event.path("message").path("content"), lines);
            case "system" -> appendSystem(event, lines);
            case "result" -> appendResult(event, lines);
            default -> {
                // 알 수 없는 이벤트는 버린다(진행률·rate limit 등 로그로 남기지 않는 값).
            }
        }
        return lines;
    }

    /** result 이벤트면 계측값/결과를 돌려준다. 그 밖의 줄은 비어 있다. */
    public static Optional<Outcome> outcome(String line) {
        Optional<JsonNode> parsed = parse(line);
        if (parsed.isEmpty() || !"result".equals(parsed.get().path("type").asText(""))) {
            return Optional.empty();
        }
        JsonNode event = parsed.get();
        String resultText = event.path("result").asText("");
        return Optional.of(new Outcome(
                resultText.isBlank() ? null : resultText,
                structuredOf(event, resultText),
                metricsOf(event),
                event.path("is_error").asBoolean(false)));
    }

    private static void appendContent(JsonNode content, List<LogLine> lines) {
        if (!content.isArray()) {
            return;
        }
        for (JsonNode block : content) {
            String type = block.path("type").asText("");
            switch (type) {
                case "thinking" -> appendText(block.path("thinking").asText(""), "✻ ", lines);
                case "text" -> appendText(block.path("text").asText(""), "", lines);
                case "tool_use" -> lines.add(new LogLine("stdout",
                        "⏺ %s %s\n".formatted(block.path("name").asText("tool"), summarize(block.path("input")))));
                default -> {
                }
            }
        }
    }

    private static void appendToolResults(JsonNode content, List<LogLine> lines) {
        if (!content.isArray()) {
            return;
        }
        for (JsonNode block : content) {
            if (!"tool_result".equals(block.path("type").asText(""))) {
                continue;
            }
            String text = textOf(block.path("content"));
            if (!text.isBlank()) {
                String firstLine = text.lines().findFirst().orElse("").trim();
                lines.add(new LogLine("stdout", "  ⎿ %s\n".formatted(truncate(firstLine))));
            }
        }
    }

    private static void appendSystem(JsonNode event, List<LogLine> lines) {
        if (!"init".equals(event.path("subtype").asText(""))) {
            return;
        }
        lines.add(new LogLine("system", "⎿ 실행 시작 (model=%s, cwd=%s)\n".formatted(
                event.path("model").asText("기본"), event.path("cwd").asText("현재 폴더"))));
    }

    private static void appendResult(JsonNode event, List<LogLine> lines) {
        String result = event.path("result").asText("");
        if (!result.isBlank()) {
            lines.add(new LogLine("stdout", result + "\n"));
        }
    }

    private static void appendText(String text, String prefix, List<LogLine> lines) {
        if (!text.isBlank()) {
            lines.add(new LogLine("stdout", prefix + text + "\n"));
        }
    }

    /** tool_result 의 content 는 문자열일 수도, [{type:text,text:...}] 배열일 수도 있다. */
    private static String textOf(JsonNode content) {
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : content) {
                builder.append(item.path("text").asText(""));
            }
            return builder.toString();
        }
        return "";
    }

    private static String summarize(JsonNode input) {
        if (input.isMissingNode() || input.isNull()) {
            return "";
        }
        for (String key : SUMMARY_KEYS) {
            JsonNode value = input.path(key);
            if (value.isTextual() && !value.asText().isBlank()) {
                return truncate(value.asText());
            }
        }
        return truncate(input.toString());
    }

    private static String truncate(String text) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= SUMMARY_LIMIT ? single : single.substring(0, SUMMARY_LIMIT) + "…";
    }

    /** structured_output 이 있으면 그걸, 없으면 result 문자열을 JSON 으로 파싱해 쓴다. */
    private static Map<String, Object> structuredOf(JsonNode event, String resultText) {
        JsonNode structured = event.path("structured_output");
        if (structured.isObject()) {
            return toMap(structured);
        }
        if (resultText.startsWith("{")) {
            Optional<JsonNode> parsed = parse(resultText);
            if (parsed.isPresent() && parsed.get().isObject()) {
                return toMap(parsed.get());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonNode node) {
        return MAPPER.convertValue(node, Map.class);
    }

    private static ExecutionMetrics metricsOf(JsonNode event) {
        JsonNode usage = event.path("usage");
        return new ExecutionMetrics(
                intOrNull(usage, "input_tokens"),
                intOrNull(usage, "output_tokens"),
                intOrNull(usage, "cache_read_input_tokens"),
                intOrNull(usage, "cache_creation_input_tokens"),
                event.path("total_cost_usd").isNumber() ? event.path("total_cost_usd").decimalValue() : null,
                event.path("duration_ms").isNumber() ? event.path("duration_ms").asLong() : null,
                event.path("num_turns").isNumber() ? event.path("num_turns").asInt() : null,
                event.path("session_id").isTextual() ? event.path("session_id").asText() : null);
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asInt() : null;
    }

    private static Optional<JsonNode> parse(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (!trimmed.startsWith("{")) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readTree(trimmed));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
