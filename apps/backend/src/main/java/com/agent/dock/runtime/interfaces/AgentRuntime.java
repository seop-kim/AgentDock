package com.agent.dock.runtime.interfaces;

import com.agent.dock.runtime.dto.AgentExecutionRequest;
import com.agent.dock.runtime.dto.AgentExecutionResult;

/**
 * Agent와 실제 AI 실행 엔진(Claude Code, Codex 등)을 분리하는 경계.
 * 새 Runtime을 추가할 때는 이 인터페이스만 구현하고 Spring Bean으로 등록하면 된다.
 */
public interface AgentRuntime {
    String getProviderKey();
    AgentExecutionResult execute(AgentExecutionRequest request) throws Exception;
    void cancel(String executionId);
}
