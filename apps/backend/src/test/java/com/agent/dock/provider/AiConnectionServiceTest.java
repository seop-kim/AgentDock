package com.agent.dock.provider;

import com.agent.dock.agent.AgentRepository;
import com.agent.dock.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConnectionServiceTest {
    @Mock AiConnectionRepository connectionRepository;
    @Mock ProbeRegistry probeRegistry;
    @Mock AgentRepository agentRepository;
    @InjectMocks AiConnectionService service;

    @Test
    void deleteDetachesAgentsThenDeletes() {
        when(connectionRepository.existsById(3L)).thenReturn(true);

        service.delete(3L);

        InOrder order = inOrder(agentRepository, connectionRepository);
        order.verify(agentRepository).detachConnection(3L);
        order.verify(connectionRepository).deleteById(3L);
    }

    @Test
    void deleteOfMissingConnectionIsNotFound() {
        when(connectionRepository.existsById(3L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(NotFoundException.class);
        verify(agentRepository, never()).detachConnection(any());
    }
}
