package com.agent.dock.execution;

import com.agent.dock.agent.Agent;
import com.agent.dock.runtime.AgentExecutionRequest;
import com.agent.dock.runtime.AgentExecutionResult;
import com.agent.dock.runtime.AgentRuntime;
import com.agent.dock.runtime.RuntimeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

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
                                        String prompt, String contractSchema, BigDecimal maxBudgetUsd) throws Exception {
        AgentRuntime runtime = runtimeRegistry.resolve(agent.getProvider().getKey().name());
        return runtime.execute(new AgentExecutionRequest(
                String.valueOf(executionId),
                prompt,
                workspacePath,
                systemPrompt,
                agent.getModel(),
                agent.getMode(),
                contractSchema,
                maxBudgetUsd,
                (content, stream) -> streamHub.line(executionId, LogStream.fromName(stream), content)));
    }
}
