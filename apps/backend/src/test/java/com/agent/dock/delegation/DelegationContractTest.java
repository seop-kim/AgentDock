package com.agent.dock.delegation;

import com.agent.dock.execution.ExecutionDecision;
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
