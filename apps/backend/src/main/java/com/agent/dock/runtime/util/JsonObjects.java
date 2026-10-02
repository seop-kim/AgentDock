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
     * 다시 시도한다. 원문이 안 되면 {@link #repair} 로 흔한 손상을 손본 뒤 한 번 더 시도한다.
     * 객체를 못 찾으면 null.
     */
    public static Map<String, Object> objectIn(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        while (start >= 0 && end > start) {
            Optional<JsonNode> parsed = parseCandidate(text.substring(start, end + 1));
            if (parsed.isPresent() && parsed.get().isObject()) {
                return toMap(parsed.get());
            }
            end = text.lastIndexOf('}', end - 1);
        }
        return null;
    }

    /** 원문을 먼저 시도하고, 실패하면 수리본으로 한 번 더 시도한다. */
    private static Optional<JsonNode> parseCandidate(String candidate) {
        Optional<JsonNode> parsed = parse(candidate);
        return parsed.isPresent() ? parsed : parse(repair(candidate));
    }

    /**
     * 모델이 흔히 망가뜨리는 부분만 손본다(계약 JSON 수리). 원문이 파싱되면 이 결과는 쓰이지 않는다.
     *
     * <ul>
     *   <li>스마트 따옴표(“ ” ‘ ’) → 보통 따옴표</li>
     *   <li>큰따옴표가 하나도 없으면 홑따옴표 JSON 으로 보고 바꾼다(문자열 안 아포스트로피를 망가뜨리지 않게 그때만)</li>
     *   <li>`//`, `/* *&#47;` 주석 제거</li>
     *   <li>닫는 괄호 앞에 남은 쉼표 제거(`{"a":1,}`)</li>
     * </ul>
     */
    static String repair(String json) {
        String fixed = json
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'');
        if (fixed.indexOf('"') < 0) {
            fixed = fixed.replace('\'', '"');
        }
        fixed = fixed.replaceAll("(?s)/\\*.*?\\*/", "");
        fixed = fixed.replaceAll("(?m)//[^\n]*$", "");
        return fixed.replaceAll(",\\s*([}\\]])", "$1");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(JsonNode node) {
        return MAPPER.convertValue(node, Map.class);
    }
}
