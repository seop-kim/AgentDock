package com.agent.dock.provider;

import com.agent.dock.common.ConflictException;
import com.agent.dock.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderServiceTest {
    @Mock AiProviderRepository providerRepository;
    @InjectMocks AiProviderService service;

    @Test
    void createRejectsDuplicateActiveKey() {
        when(providerRepository.existsByKeyAndDeletedAtIsNull(ProviderKey.CODEX)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateAiProviderRequest(ProviderKey.CODEX, "Codex", null)))
                .isInstanceOf(ConflictException.class);
        verify(providerRepository, never()).save(any());
    }

    @Test
    void createAllowsKeyWhoseOnlyOtherRowIsDeleted() {
        // 삭제된 행은 existsByKeyAndDeletedAtIsNull 에서 제외되므로 false 로 돌아온다
        when(providerRepository.existsByKeyAndDeletedAtIsNull(ProviderKey.CODEX)).thenReturn(false);
        when(providerRepository.save(any(AiProvider.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(new CreateAiProviderRequest(ProviderKey.CODEX, "Codex", null));

        assertThat(response.key()).isEqualTo(ProviderKey.CODEX);
        assertThat(response.enabled()).isFalse();
    }

    @Test
    void deleteMarksDeletedAtWithoutTouchingAnythingElse() {
        AiProvider provider = new AiProvider();
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(provider));

        service.delete(1L);

        verify(providerRepository).save(provider);
        assertThat(provider.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteOfMissingOrAlreadyDeletedProviderIsNotFound() {
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(NotFoundException.class);
        verify(providerRepository, never()).save(any());
    }

    @Test
    void setEnabledTogglesTheRuntime() {
        AiProvider provider = new AiProvider();
        provider.setEnabled(false);
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(provider));
        when(providerRepository.save(any(AiProvider.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.setEnabled(1L, true);

        assertThat(response.enabled()).isTrue();
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    void updateCapabilitiesNormalizesListsAndKeepsOtherKeys() {
        AiProvider provider = new AiProvider();
        Map<String, Object> existing = new HashMap<>();
        existing.put("custom", "keep-me");
        provider.setCapabilities(existing);
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(provider));
        when(providerRepository.save(any(AiProvider.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateCapabilities(1L, new ProviderCapabilities(
                List.of(" opus ", "opus", "", "  ", "sonnet"), List.of("plan", " plan "), "  note  "));

        assertThat(response.capabilities()).containsEntry("custom", "keep-me");
        assertThat(response.capabilities().get("models")).isEqualTo(List.of("opus", "sonnet"));
        assertThat(response.capabilities().get("modes")).isEqualTo(List.of("plan"));
        assertThat(response.capabilities().get("notes")).isEqualTo("note");
    }

    @Test
    void updateCapabilitiesRemovesNotesWhenBlank() {
        AiProvider provider = new AiProvider();
        Map<String, Object> existing = new HashMap<>();
        existing.put("notes", "old note");
        provider.setCapabilities(existing);
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(provider));
        when(providerRepository.save(any(AiProvider.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateCapabilities(1L, new ProviderCapabilities(null, null, "   "));

        assertThat(response.capabilities()).doesNotContainKey("notes");
        assertThat(response.capabilities().get("models")).isEqualTo(List.of());
    }

    @Test
    void updateCapabilitiesOfDeletedProviderIsNotFound() {
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCapabilities(1L, new ProviderCapabilities(List.of(), List.of(), null)))
                .isInstanceOf(NotFoundException.class);
        verify(providerRepository, never()).save(any());
    }
}
