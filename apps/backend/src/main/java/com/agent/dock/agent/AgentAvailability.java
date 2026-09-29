package com.agent.dock.agent;

import com.agent.dock.provider.AiConnection;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.ConnectionStatus;

import java.util.List;

/**
 * Agent 를 지금 실행할 수 있는지 판단하는 유일한 규칙. 저장하지 않고 매번 파생한다.
 * Agent 응답(화면 표시)과 실행 가드(ExecutionService)가 같은 규칙을 쓴다.
 */
public final class AgentAvailability {

    public enum Reason { PROVIDER_DELETED, CONNECTION_NOT_CONNECTED }

    public record Result(boolean available, Reason reason) {
        public static final Result OK = new Result(true, null);

        public String message() {
            if (reason == null) {
                return "Agent is available";
            }
            return switch (reason) {
                case PROVIDER_DELETED -> "Provider was deleted; assign another provider to this agent";
                case CONNECTION_NOT_CONNECTED -> "Provider connection is not CONNECTED; check it in Agent 연결 설정";
            };
        }
    }

    private AgentAvailability() {
    }

    public static Result evaluate(boolean providerDeleted, List<ConnectionStatus> connectionStatuses) {
        if (providerDeleted) {
            return new Result(false, Reason.PROVIDER_DELETED);
        }
        boolean connected = connectionStatuses.stream().anyMatch(status -> status == ConnectionStatus.CONNECTED);
        return connected ? Result.OK : new Result(false, Reason.CONNECTION_NOT_CONNECTED);
    }

    public static Result evaluate(AiProvider provider, List<AiConnection> connections) {
        return evaluate(provider.getDeletedAt() != null, connections.stream().map(AiConnection::getStatus).toList());
    }
}
