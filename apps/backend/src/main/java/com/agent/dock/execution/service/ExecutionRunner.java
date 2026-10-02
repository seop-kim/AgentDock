package com.agent.dock.execution.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.LogStream;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.runtime.dto.AgentExecutionRequest;
import com.agent.dock.runtime.dto.AgentExecutionResult;
import com.agent.dock.runtime.interfaces.AgentRuntime;
import com.agent.dock.runtime.service.RuntimeRegistry;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 실행 한 스텝(CLI 한 번)을 돌린다.
 * 스트림 버킷은 호출부가 열고 닫는다 — 위임은 한 실행을 여러 스텝으로 돌리기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class ExecutionRunner {

    private final RuntimeRegistry runtimeRegistry;
    private final ExecutionStreamHub streamHub;
    private final ExecutionRepository executionRepository;

    public AgentExecutionResult runStep(Long executionId, Agent agent, String cwd, String systemPrompt,
                                        String prompt, BigDecimal maxBudgetUsd) throws Exception {
        AgentRuntime runtime = runtimeRegistry.resolve(agent.getProvider().getKey().name());
        // 같은 실행의 다음 스텝은 **앞 스텝이 만든 CLI 세션을 이어받는다**(맥락을 다시 설명하지 않게 = 입력 토큰 절약).
        // 실행 하나는 같은 에이전트(같은 런타임)로만 도므로, 자기 세션을 물려주는 것은 안전하다.
        // 첫 스텝은 세션이 없으므로 새로 시작한다.
        String resumeSessionId = executionRepository.findById(executionId)
                .map(Execution::getSessionId)
                .filter(session -> session != null && !session.isBlank())
                .orElse(null);
        return runtime.execute(new AgentExecutionRequest(
                String.valueOf(executionId),
                prompt,
                cwd,
                systemPrompt,
                agent.getModel(),
                agent.getMode(),
                maxBudgetUsd,
                (content, stream) -> streamHub.line(executionId, LogStream.fromName(stream), content),
                resumeSessionId,
                false));
    }
}
