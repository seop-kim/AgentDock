package com.agent.dock.runtime.util;

import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * CLI 출력(JSONL)에서 JSON 을 다루는 공통 헬퍼.
 *
 * 결과 텍스트에서 <b>JSON 객체 하나를 찾는 규칙</b>은 런타임마다 같다(모델이 코드블록이나
 * 앞뒤 설명을 섞어 내도 동작해야 한다). Claude 파서와 Command Code 파서가 같은 규칙을
 * 쓰도록 이곳에 한 번만 둔다(중복 금지).
 */
public final class JsonObjects {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonObjects() {
    }

    /** 한 줄을 JsonNode 로 파싱한다. '{' 로 시작하지 않거나 파싱에 실패하면 빈 값. */
    public static Optional<JsonNode> parse(String line) {
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

    /**
     * 텍스트에서 JSON 객체 하나를 찾는다. 모델이 코드블록(```json … ```)이나 앞뒤 설명을
     * 섞어 내도 동작하도록 첫 '{' 부터 마지막 '}' 까지를 파싱하고, 실패하면 끝을 하나씩 줄여
     * 다시 시도한다. 객체를 못 찾으면 null.
     */
    public static Map<String, Object> objectIn(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        while (start >= 0 && end > start) {
            Optional<JsonNode> parsed = parse(text.substring(start, end + 1));
            if (parsed.isPresent() && parsed.get().isObject()) {
                return toMap(parsed.get());
            }
            end = text.lastIndexOf('}', end - 1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(JsonNode node) {
        return MAPPER.convertValue(node, Map.class);
    }
}
