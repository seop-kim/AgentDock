package com.agent.dock.delegation.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프롬프트에 스키마·요청·로스터가 들어가고, 긴 지시·문맥은 파일을 가리키는지 고정한다(계약은 프롬프트로만 강제한다).
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
    void judgementPromptReferencesPreviousStepContextFile() {
        String prompt = DelegationPrompts.judgement("test", "", "요청",
                ".agentdock/prompts/9-step1.md — 이 파일을 먼저 읽고 그 결과를 반영하세요.", 4);

        assertThat(prompt).contains("[지난 단계 결과]").contains(".agentdock/prompts/9-step1.md");
    }

    @Test
    void judgementPromptOmitsTheProgressSectionWhenThereIsNone() {
        String prompt = DelegationPrompts.judgement("test", "", "요청", "", 4);

        assertThat(prompt).doesNotContain("[지난 단계 결과]");
    }

    @Test
    void repairPromptRepeatsTheSchema() {
        String prompt = DelegationPrompts.repair("아무 텍스트");

        assertThat(prompt).contains("아무 텍스트").contains(ContractSchemas.JUDGEMENT);
    }

    @Test
    void workPromptCarriesTheInstructionAndHandoffSchema() {
        String prompt = DelegationPrompts.work("지시 파일: .agentdock/prompts/42.md — 이 파일을 먼저 읽고 그 내용대로 작업하세요.");

        assertThat(prompt).contains(".agentdock/prompts/42.md").contains(ContractSchemas.WORK);
    }

    @Test
    void childInstructionRendersAllSections() {
        String doc = DelegationPrompts.childInstruction("원래 요청 본문", "맡은 일 본문", "기대 결과 본문",
                "지난 결과 본문", List.of(".agentdock/attachments/abcd1234-report.pdf"));

        assertThat(doc).contains("## 원래 요청").contains("원래 요청 본문")
                .contains("## 맡은 일").contains("맡은 일 본문")
                .contains("## 기대 결과").contains("기대 결과 본문")
                .contains("## 지난 결과").contains("지난 결과 본문")
                .contains("## 첨부 파일").contains(".agentdock/attachments/abcd1234-report.pdf");
    }

    @Test
    void childInstructionOmitsEmptySections() {
        String doc = DelegationPrompts.childInstruction("요청", "일", "", "", List.of());

        assertThat(doc).contains("## 원래 요청").contains("## 맡은 일")
                .doesNotContain("## 기대 결과").doesNotContain("## 지난 결과").doesNotContain("## 첨부 파일");
    }

    @Test
    void instructionReferencePointsAtThePromptFileAndKeepsItShort() {
        String prompt = DelegationPrompts.instructionReference(".agentdock/prompts/42.md", "파일을 만들어 주세요");

        assertThat(prompt).startsWith("지시 파일: .agentdock/prompts/42.md")
                .contains("이 파일을 먼저 읽고").contains("기대 결과: 파일을 만들어 주세요");
        // 한 줄로 유지한다(1~3줄 제한).
        assertThat(prompt.lines().count()).isEqualTo(1);
    }

    @Test
    void instructionReferenceOmitsExpectsWhenBlank() {
        assertThat(DelegationPrompts.instructionReference(".agentdock/prompts/42.md", ""))
                .doesNotContain("기대 결과");
    }

    @Test
    void inlineInstructionCarriesRequestOrderAndExpects() {
        String prompt = DelegationPrompts.inlineInstruction("원래 요청", "맡은 일", "기대 결과");

        assertThat(prompt).contains("[원래 요청]").contains("원래 요청")
                .contains("[맡은 일]").contains("맡은 일")
                .contains("[기대 결과]").contains("기대 결과");
    }

    @Test
    void promptFileNamesUseTheExecutionIdAndStep() {
        assertThat(DelegationPrompts.childPromptFileName(42L)).isEqualTo("42.md");
        assertThat(DelegationPrompts.stepPromptFileName(50L, 2)).isEqualTo("50-step2.md");
        assertThat(DelegationPrompts.promptFileReference("42.md")).isEqualTo(".agentdock/prompts/42.md");
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
