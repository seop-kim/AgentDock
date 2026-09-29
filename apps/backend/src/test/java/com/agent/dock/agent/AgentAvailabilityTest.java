package com.agent.dock.agent;

import com.agent.dock.provider.ConnectionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAvailabilityTest {

    @Test
    void deletedProviderIsUnavailableEvenWhenConnected() {
        var result = AgentAvailability.evaluate(true, List.of(ConnectionStatus.CONNECTED));

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AgentAvailability.Reason.PROVIDER_DELETED);
    }

    @Test
    void noConnectionIsUnavailable() {
        var result = AgentAvailability.evaluate(false, List.of());

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AgentAvailability.Reason.CONNECTION_NOT_CONNECTED);
    }

    @ParameterizedTest
    @EnumSource(value = ConnectionStatus.class, names = {"DISCONNECTED", "ERROR"})
    void notConnectedStatusIsUnavailable(ConnectionStatus status) {
        var result = AgentAvailability.evaluate(false, List.of(status));

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AgentAvailability.Reason.CONNECTION_NOT_CONNECTED);
    }

    @Test
    void connectedIsAvailable() {
        var result = AgentAvailability.evaluate(false, List.of(ConnectionStatus.CONNECTED));

        assertThat(result.available()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void anyConnectedAmongLegacyMultipleConnectionsIsAvailable() {
        var result = AgentAvailability.evaluate(false, List.of(ConnectionStatus.ERROR, ConnectionStatus.CONNECTED));

        assertThat(result.available()).isTrue();
    }

    @Test
    void messageDiffersPerReason() {
        assertThat(AgentAvailability.evaluate(true, List.of()).message()).contains("deleted");
        assertThat(AgentAvailability.evaluate(false, List.of()).message()).contains("not CONNECTED");
    }
}
