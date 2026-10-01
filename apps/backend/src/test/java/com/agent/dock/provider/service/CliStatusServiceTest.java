package com.agent.dock.provider.service;

import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.provider.domain.AiProvider;
import com.agent.dock.provider.domain.ProviderKey;
import com.agent.dock.provider.interfaces.AiRuntimeCli;
import com.agent.dock.provider.repository.AiProviderRepository;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CliStatusServiceTest {
    @Mock AiProviderRepository providerRepository;
    @Mock CliRegistry cliRegistry;
    @InjectMocks CliStatusService service;

    @Test
    void providerWithoutCliImplementationReportsWhy() {
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(provider(ProviderKey.CODEX)));
        when(cliRegistry.find(ProviderKey.CODEX)).thenReturn(Optional.empty());

        var status = service.check(1L);

        assertThat(status.runnable()).isFalse();
        assertThat(status.resolvedPath()).isNull();
        assertThat(status.detail()).contains("CLI 정보를 확인할 수 없습니다");
    }

    @Test
    void missingExecutableReportsNotFound() {
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(provider(ProviderKey.CLAUDE_CODE)));
        when(cliRegistry.find(ProviderKey.CLAUDE_CODE))
                .thenReturn(Optional.of(cli("definitely-not-installed-xyz")));

        var status = service.check(1L);

        assertThat(status.runnable()).isFalse();
        assertThat(status.resolvedPath()).isNull();
        assertThat(status.detail()).contains("찾을 수 없습니다");
    }

    @Test
    void missingProviderIsNotFound() {
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.check(1L)).isInstanceOf(NotFoundException.class);
    }

    private AiProvider provider(ProviderKey key) {
        AiProvider provider = new AiProvider();
        provider.setKey(key);
        return provider;
    }

    private AiRuntimeCli cli(String binary) {
        return new AiRuntimeCli() {
            @Override
            public ProviderKey providerKey() {
                return ProviderKey.CLAUDE_CODE;
            }

            @Override
            public String binary() {
                return binary;
            }
        };
    }
}
