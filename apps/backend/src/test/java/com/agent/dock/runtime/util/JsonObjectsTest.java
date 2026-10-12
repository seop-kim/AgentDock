package com.agent.dock.runtime.util;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약 JSON 찾기·수리: 모델이 코드블록·홑따옴표·군더더기 쉼표·주석·스마트 따옴표를 섞어 내도 계약을 건져낸다.
 * 원문이 그대로 파싱되면 수리본은 쓰지 않는다.
 */
class JsonObjectsTest {

    @Test
    void findsTheObjectInsideCodeFencesAndProse() {
        Map<String, Object> found = JsonObjects.objectIn("""
                결과입니다:
                ```json
                {"action":"done","summary":"끝났습니다"}
                ```
                이상입니다.""");

        assertThat(found).containsEntry("action", "done").containsEntry("summary", "끝났습니다");
    }

    @Test
    void repairsTrailingCommaBeforeClosingBrace() {
        assertThat(JsonObjects.objectIn("{\"action\":\"done\",\"summary\":\"ok\",}"))
                .containsEntry("action", "done");
    }

    @Test
    void repairsSingleQuotedJsonOnlyWhenThereAreNoDoubleQuotes() {
        assertThat(JsonObjects.objectIn("{'action':'ask','question':'어느 쪽으로 갈까요?'}"))
                .containsEntry("action", "ask")
                .containsEntry("question", "어느 쪽으로 갈까요?");
    }

    @Test
    void keepsApostrophesInsideDoubleQuotedStrings() {
        assertThat(JsonObjects.objectIn("{\"action\":\"done\",\"summary\":\"it's done\"}"))
                .containsEntry("summary", "it's done");
    }

    @Test
    void repairsSmartQuotesAndComments() {
        String text = "{“action”:“done”, // 사람이 읽는 주석\n “summary”:“끝”}";

        assertThat(JsonObjects.objectIn(text)).containsEntry("action", "done").containsEntry("summary", "끝");
    }

    @Test
    void returnsNullWhenThereIsNoObject() {
        assertThat(JsonObjects.objectIn("계약 없음")).isNull();
        assertThat(JsonObjects.objectIn(null)).isNull();
    }
}
