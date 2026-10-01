package com.agent.dock.project.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 구성도 배치 저장(캔버스 드래그 결과). 좌표는 화면 월드 좌표이고 음수도 된다.
 * 좌표가 null 이면 자동 배치를 따르게 된다.
 *
 * @param agents 에이전트 노드의 위치·배치 여부. placed 를 주지 않으면 기존 값을 유지한다.
 * @param groups 그룹 상자의 위치.
 */
public record ProjectLayoutRequest(
        List<AgentPlacement> agents,
        List<GroupPlacement> groups
) {
    public record AgentPlacement(
            @NotNull Long agentId,
            Double x,
            Double y,
            Boolean placed
    ) {
    }

    public record GroupPlacement(
            @NotNull Long groupId,
            Double x,
            Double y
    ) {
    }
}
