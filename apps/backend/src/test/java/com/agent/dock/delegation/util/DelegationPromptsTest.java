package com.agent.dock.delegation.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프롬프트에 스키마와 요청이 실제로 들어가는지 고정한다(계약은 프롬프트로만 강제한다).
 */
class DelegationPromptsTest {

    @Test
    void judgementPromptCarriesRosterRequestAndSchema() {
        String roster = "- 팀 \"백엔드 팀\" · 리더: 리드(id=7) · 멤버: A(id=9)\n";

        String prompt = DelegationPrompts.judgement("test", roster, "1+1 을 계산해줘", "", 4);

        assertThat(prompt).contains("백엔드 팀").contains("id=7").contains("1+1 을 계산해줘")
                .contains(ContractSchemas.JUDGEMENT);
    }

    @Test
    void judgementPromptCarriesPreviousStepResults() {
        String prompt = DelegationPrompts.judgement("test", "", "요청", "- 백엔드 A(id=9) [완료]: 1+1=2\n", 4);

        assertThat(prompt).contains("[지난 단계 결과]").contains("1+1=2");
    }

    @Test
    void repairPromptRepeatsTheSchema() {
        String prompt = DelegationPrompts.repair("아무 텍스트");

        assertThat(prompt).contains("아무 텍스트").contains(ContractSchemas.JUDGEMENT);
    }

    @Test
    void workPromptCarriesRequestOrderAndHandoffSchema() {
        String prompt = DelegationPrompts.work("원래 요청", "1+1 을 계산하세요");

        assertThat(prompt).contains("원래 요청").contains("1+1 을 계산하세요").contains(ContractSchemas.WORK);
    }

    @Test
    void childResultsListAgentSummaryAndChangedFiles() {
        String progress = DelegationPrompts.childResults(List.of(
                new DelegationPrompts.ChildOutcome("백엔드 A", 9L, "완료", "1+1=2", List.of("a.txt"), ""),
                new DelegationPrompts.ChildOutcome("백엔드 B", 10L, "실패", "", List.of(), "계약 위반")));

        assertThat(progress).contains("백엔드 A").contains("(id=9)").contains("1+1=2").contains("a.txt")
                .contains("백엔드 B").contains("(요약 없음)").contains("계약 위반");
    }
}
