package com.agent.dock.agent;

import com.agent.dock.common.NotFoundException;
import com.agent.dock.permission.PermissionProfile;
import com.agent.dock.permission.PermissionProfileRepository;
import com.agent.dock.provider.AiConnection;
import com.agent.dock.provider.AiConnectionRepository;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.AiProviderRepository;
import com.agent.dock.provider.ConnectionStatus;
import com.agent.dock.provider.ProviderKey;
import com.agent.dock.role.AgentRole;
import com.agent.dock.role.AgentRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {
    @Mock AgentRepository agentRepository;
    @Mock AgentRoleRepository roleRepository;
    @Mock PermissionProfileRepository permissionProfileRepository;
    @Mock AiProviderRepository providerRepository;
    @Mock AiConnectionRepository connectionRepository;
    @InjectMocks AgentService service;

    private AiProvider provider(long id, boolean deleted) {
        AiProvider provider = new AiProvider();
        provider.setId(id);
        provider.setKey(ProviderKey.CLAUDE_CODE);
        provider.setName("Claude Code");
        provider.setDeletedAt(deleted ? Instant.now() : null);
        return provider;
    }

    private Agent agentWith(AiProvider provider) {
        Agent agent = new Agent();
        agent.setId(1L);
        agent.setName("Dev A");
        agent.setRole(new AgentRole());
        agent.setPermissionProfile(new PermissionProfile());
        agent.setProvider(provider);
        return agent;
    }

    @Test
    void findAllMarksAgentOfDeletedProviderUnavailable() {
        when(agentRepository.findAllWithRelations()).thenReturn(List.of(agentWith(provider(7L, true))));

        var responses = service.findAll();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).available()).isFalse();
        assertThat(responses.get(0).unavailableReason()).isEqualTo("PROVIDER_DELETED");
    }

    @Test
    void findAllMarksAgentWithConnectedConnectionAvailable() {
        AiConnection connection = new AiConnection();
        connection.setProviderId(7L);
        connection.setStatus(ConnectionStatus.CONNECTED);
        when(agentRepository.findAllWithRelations()).thenReturn(List.of(agentWith(provider(7L, false))));
        when(connectionRepository.findByProviderIdIn(any())).thenReturn(List.of(connection));

        var responses = service.findAll();

        assertThat(responses.get(0).available()).isTrue();
        assertThat(responses.get(0).unavailableReason()).isNull();
    }

    @Test
    void assignProviderToDeletedOrMissingProviderIsNotFoundAndChangesNothing() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agentWith(provider(7L, true))));
        when(providerRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignProvider(1L, 9L)).isInstanceOf(NotFoundException.class);
        verify(agentRepository, never()).save(any());
    }

    @Test
    void assignProviderSwitchesProviderAndClearsConnection() {
        Agent agent = agentWith(provider(7L, true));
        agent.setConnection(new AiConnection());
        AiProvider replacement = provider(9L, false);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(providerRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(replacement));
        when(agentRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(agent));

        service.assignProvider(1L, 9L);

        verify(agentRepository).save(agent);
        assertThat(agent.getProvider()).isSameAs(replacement);
        assertThat(agent.getConnection()).isNull();
    }
}
