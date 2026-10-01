package com.agent.dock.delegation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ContractSchemasTest {

    /**
     * 줄바꿈이 들어가면 CLI 가 "--json-schema is not valid JSON" 으로 거부한다(실측).
     * 그래서 스키마는 항상 한 줄이어야 하고, 동시에 유효한 JSON 이어야 한다.
     */
    @Test
    void schemasAreSingleLineAndValidJson() {
        for (String schema : new String[] {ContractSchemas.JUDGEMENT, ContractSchemas.WORK}) {
            assertThat(schema).doesNotContain("\n").doesNotContain("\r");
            assertThat(isJson(schema)).as(schema).isTrue();
        }
    }

    private boolean isJson(String value) {
        try {
            new ObjectMapper().readTree(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
