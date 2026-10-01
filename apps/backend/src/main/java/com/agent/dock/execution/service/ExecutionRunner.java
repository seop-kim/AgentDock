package com.agent.dock.execution.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.LogStream;
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

    public AgentExecutionResult runStep(Long executionId, Agent agent, String workspacePath, String systemPrompt,
                                        String prompt, BigDecimal maxBudgetUsd) throws Exception {
        AgentRuntime runtime = runtimeRegistry.resolve(agent.getProvider().getKey().name());
        return runtime.execute(new AgentExecutionRequest(
                String.valueOf(executionId),
                prompt,
                workspacePath,
                systemPrompt,
                agent.getModel(),
                agent.getMode(),
                maxBudgetUsd,
                (content, stream) -> streamHub.line(executionId, LogStream.fromName(stream), content)));
    }
}
