package com.agent.dock.delegation;

/**
 * CLI `--json-schema` 로 강제하는 계약 스키마.
 * 프롬프트로 부탁하는 대신 스키마로 강제한다(파싱 실패 확률을 낮춘다).
 */
public final class ContractSchemas {

    /** 판단 실행(마스터·그룹 리더): 위임할 대상과 각자에게 줄 지시, 또는 완료 요약. */
    public static final String JUDGEMENT = """
            {"type":"object",
             "properties":{
               "action":{"type":"string","enum":["delegate","done"]},
               "targets":{"type":"array","items":{
                 "type":"object",
                 "properties":{
                   "agentId":{"type":"integer"},
                   "prompt":{"type":"string"},
                   "expects":{"type":"string"}},
                 "required":["agentId","prompt"],
                 "additionalProperties":false}},
               "summary":{"type":"string"}},
             "required":["action"],
             "additionalProperties":false}
            """;

    /** 작업 실행(실제로 일하는 에이전트): 무엇을 했고 어떤 파일을 바꿨는지. */
    public static final String WORK = """
            {"type":"object",
             "properties":{
               "summary":{"type":"string"},
               "changedFiles":{"type":"array","items":{"type":"string"}}},
             "required":["summary"],
             "additionalProperties":false}
            """;

    private ContractSchemas() {
    }
}
