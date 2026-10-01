package com.agent.dock.agent.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.provider.domain.ConnectionStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentAvailabilityTest {

    @Test
    void deletedRuntimeIsUnavailableEvenWhenEnabledAndConnected() {
        var result = AgentAvailability.evaluate(true, true, ConnectionStatus.CONNECTED);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AgentAvailability.Reason.PROVIDER_DELETED);
    }

    @Test
    void disabledRuntimeIsUnavailableEvenWhenConnected() {
        var result = AgentAvailability.evaluate(false, false, ConnectionStatus.CONNECTED);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AgentAvailability.Reason.RUNTIME_DISABLED);
    }

    @Test
    void workspaceWithoutConnectedStatusIsUnavailable() {
        var result = AgentAvailability.evaluate(false, true, ConnectionStatus.ERROR);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AgentAvailability.Reason.CONNECTION_NOT_CONNECTED);
    }

    @Test
    void unknownWorkspaceStatusIsUnavailable() {
        var result = AgentAvailability.evaluate(false, true, ConnectionStatus.DISCONNECTED);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AgentAvailability.Reason.CONNECTION_NOT_CONNECTED);
    }

    @Test
    void enabledRuntimeInConnectedWorkspaceIsAvailable() {
        var result = AgentAvailability.evaluate(false, true, ConnectionStatus.CONNECTED);

        assertThat(result.available()).isTrue();
        assertThat(result.reason()).isNull();
        assertThat(result.message()).contains("available");
    }

    @Test
    void deletionTakesPrecedenceOverDisabledAndStatus() {
        var result = AgentAvailability.evaluate(true, false, ConnectionStatus.DISCONNECTED);

        assertThat(result.reason()).isEqualTo(AgentAvailability.Reason.PROVIDER_DELETED);
    }

    @Test
    void messageDiffersPerReason() {
        assertThat(AgentAvailability.evaluate(true, true, ConnectionStatus.CONNECTED).message()).contains("deleted");
        assertThat(AgentAvailability.evaluate(false, false, ConnectionStatus.CONNECTED).message()).contains("turned off");
        assertThat(AgentAvailability.evaluate(false, true, ConnectionStatus.ERROR).message()).contains("not verified");
    }
}
