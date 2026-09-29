package com.agent.dock.agent;

import com.agent.dock.provider.ConnectionStatus;

/**
 * Agent 를 지금 실행할 수 있는지 판단하는 유일한 규칙. 저장하지 않고 매번 파생한다.
 * 실행 조건: 런타임이 살아 있고(논리 삭제 아님) 켜져 있으며(enabled),
 * 그 프로젝트의 (기본) 워크스페이스에서 런타임 상태가 CONNECTED 여야 한다.
 */
public final class AgentAvailability {

    public enum Reason { PROVIDER_DELETED, RUNTIME_DISABLED, CONNECTION_NOT_CONNECTED }

    public record Result(boolean available, Reason reason) {
        public static final Result OK = new Result(true, null);

        public String message() {
            if (reason == null) {
                return "Agent is available";
            }
            return switch (reason) {
                case PROVIDER_DELETED -> "Runtime was deleted; assign another runtime to this agent";
                case RUNTIME_DISABLED -> "Runtime is turned off; enable it in 에이전트 설정";
                case CONNECTION_NOT_CONNECTED -> "Runtime is not verified in the project default workspace; check it in 워크스페이스";
            };
        }
    }

    private AgentAvailability() {
    }

    public static Result evaluate(boolean providerDeleted, boolean runtimeEnabled, ConnectionStatus workspaceStatus) {
        if (providerDeleted) {
            return new Result(false, Reason.PROVIDER_DELETED);
        }
        if (!runtimeEnabled) {
            return new Result(false, Reason.RUNTIME_DISABLED);
        }
        if (workspaceStatus != ConnectionStatus.CONNECTED) {
            return new Result(false, Reason.CONNECTION_NOT_CONNECTED);
        }
        return Result.OK;
    }
}
