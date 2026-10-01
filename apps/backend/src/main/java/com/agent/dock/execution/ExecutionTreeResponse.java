package com.agent.dock.execution;

import java.util.List;

/**
 * 실행 트리 조회 결과. 화면이 순서대로 그릴 수 있게 평평한 목록 + 깊이로 돌려준다.
 */
public record ExecutionTreeResponse(Long rootExecutionId, List<ExecutionTreeNode> nodes) {

    public record ExecutionTreeNode(
            ExecutionResponse execution,
            int depth,
            String agentName,
            String targetAgentName,
            long logCount
    ) {
    }
}
