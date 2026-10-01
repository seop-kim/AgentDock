package com.agent.dock.delegation.dto;

import com.agent.dock.execution.domain.ExecutionDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DelegationContractTest {

    @Test
    void readsDoneContract() {
        DelegationContract contract = DelegationContract.parse(Map.of("action", "done", "summary", "끝냈습니다")).orElseThrow();

        assertThat(contract.action()).isEqualTo(ExecutionDecision.DONE);
        assertThat(contract.summary()).isEqualTo("끝냈습니다");
        assertThat(contract.orders()).isEmpty();
    }

    @Test
    void readsDelegateContractWithTargets() {
        Map<String, Object> payload = Map.of(
                "action", "delegate",
                "targets", List.of(
                        Map.of("agentId", 12, "prompt", "백엔드를 봐주세요", "expects", "원인 한 줄"),
                        Map.of("agentId", 20, "prompt", "프론트를 봐주세요")));

        DelegationContract contract = DelegationContract.parse(payload).orElseThrow();

        assertThat(contract.action()).isEqualTo(ExecutionDecision.DELEGATE);
        assertThat(contract.orders()).hasSize(2);
        assertThat(contract.orders().getFirst().agentId()).isEqualTo(12L);
        assertThat(contract.orders().getFirst().expects()).isEqualTo("원인 한 줄");
        assertThat(contract.orders().get(1).expects()).isEmpty();
    }

    @Test
    void acceptsAgentIdGivenAsNumericString() {
        DelegationContract contract = DelegationContract.parse(
                Map.of("action", "delegate", "targets", List.of(Map.of("agentId", "12", "prompt", "봐주세요"))))
                .orElseThrow();

        assertThat(contract.orders().getFirst().agentId()).isEqualTo(12L);
    }

    @Test
    void readsAskContractWithQuestionAndOptions() {
        DelegationContract contract = DelegationContract.parse(Map.of(
                "action", "ask",
                "question", "새 파일 이름을 무엇으로 할까요?",
                "options", List.of("A안", "B안"))).orElseThrow();

        assertThat(contract.action()).isEqualTo(ExecutionDecision.ASK);
        assertThat(contract.question()).isEqualTo("새 파일 이름을 무엇으로 할까요?");
        assertThat(contract.options()).containsExactly("A안", "B안");
        assertThat(contract.orders()).isEmpty();
    }

    @Test
    void readsAskContractWithoutOptions() {
        DelegationContract contract = DelegationContract.parse(
                Map.of("action", "ask", "question", "이대로 진행할까요?")).orElseThrow();

        assertThat(contract.action()).isEqualTo(ExecutionDecision.ASK);
        assertThat(contract.options()).isEmpty();
    }

    @Test
    void rejectsAskWithoutAQuestion() {
        assertThat(DelegationContract.parse(Map.of("action", "ask"))).isEmpty();
        assertThat(DelegationContract.parse(Map.of("action", "ask", "question", "  "))).isEmpty();
    }

    @Test
    void ignoresNonStringOptions() {
        DelegationContract contract = DelegationContract.parse(Map.of(
                "action", "ask",
                "question", "무엇으로 할까요?",
                "options", List.of("A안", 3, Map.of("x", 1)))).orElseThrow();

        assertThat(contract.options()).containsExactly("A안");
    }

    @Test
    void rejectsPayloadsThatAreNotTheContract() {
        assertThat(DelegationContract.parse(null)).isEmpty();
        assertThat(DelegationContract.parse(Map.of())).isEmpty();
        assertThat(DelegationContract.parse(Map.of("action", "maybe"))).isEmpty();
        assertThat(DelegationContract.parse(Map.of("action", "delegate"))).isEmpty();
        assertThat(DelegationContract.parse(Map.of("action", "delegate", "targets", List.of()))).isEmpty();
    }

    @Test
    void rejectsTargetsWithoutAgentIdOrPrompt() {
        assertThat(DelegationContract.parse(Map.of("action", "delegate",
                "targets", List.of(Map.of("prompt", "봐주세요"))))).isEmpty();
        assertThat(DelegationContract.parse(Map.of("action", "delegate",
                "targets", List.of(Map.of("agentId", 12))))).isEmpty();
        assertThat(DelegationContract.parse(Map.of("action", "delegate",
                "targets", List.of("문자열")))).isEmpty();
    }
}
