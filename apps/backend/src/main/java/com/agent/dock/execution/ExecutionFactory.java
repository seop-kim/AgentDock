package com.agent.dock.execution;

import com.agent.dock.agent.AgentRepository;
import com.agent.dock.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 실행 행 생성. 트리 규칙(루트는 root_execution_id 가 비어 있다)을 한 곳에서 지킨다.
 */
@Component
@RequiredArgsConstructor
public class ExecutionFactory {

    private final ExecutionRepository executionRepository;
    private final AgentRepository agentRepository;
    private final WorkspaceRepository workspaceRepository;

    /** 명령을 받은 루트 실행(보통 마스터). */
    public Execution createRoot(ExecutionGuard.Target target, String prompt, Long taskId) {
        Execution execution = base(target, prompt, taskId, null);
        return executionRepository.save(execution);
    }

    /** 위임받은 자식 실행. 부모와 트리 루트를 함께 남긴다. */
    public Execution createChild(ExecutionGuard.Target target, String prompt, Long taskId, Execution parent, Execution root) {
        Execution execution = base(target, prompt, taskId, parent);
        execution.setRootExecution(executionRepository.getReferenceById(root.getId()));
        return executionRepository.save(execution);
    }

    private Execution base(ExecutionGuard.Target target, String prompt, Long taskId, Execution parent) {
        Execution execution = new Execution();
        execution.setAgent(agentRepository.getReferenceById(target.agentId()));
        execution.setWorkspace(workspaceRepository.getReferenceById(target.workspaceId()));
        execution.setTaskId(taskId);
        execution.setPrompt(prompt);
        execution.setStatus(ExecutionStatus.PENDING);
        execution.setParentExecution(parent);
        return execution;
    }
}
