package com.agent.dock.provider;

import com.agent.dock.common.ConflictException;
import com.agent.dock.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiProviderService {
    private final AiProviderRepository providerRepository;

    /** 논리 삭제되지 않은 런타임만 반환한다. */
    public List<AiProviderResponse> findAll() {
        return providerRepository.findByDeletedAtIsNullOrderByNameAsc().stream().map(AiProviderResponse::from).toList();
    }

    @Transactional
    public AiProviderResponse create(CreateAiProviderRequest request) {
        if (providerRepository.existsByKeyAndDeletedAtIsNull(request.key())) {
            throw new ConflictException("Provider already registered: " + request.key());
        }
        AiProvider provider = new AiProvider();
        provider.setKey(request.key());
        provider.setName(request.name());
        provider.setCapabilities(request.capabilities() != null ? request.capabilities() : new HashMap<>());
        return AiProviderResponse.from(providerRepository.save(provider));
    }

    /** 논리 삭제. 이 런타임을 쓰던 에이전트는 남고 "사용 불가"가 된다. */
    @Transactional
    public void delete(Long id) {
        AiProvider provider = findActive(id);
        provider.setDeletedAt(Instant.now());
        providerRepository.save(provider);
    }

    /** 런타임 on/off. 켜져 있어야 에이전트에 할당하거나 실행할 수 있다. */
    @Transactional
    public AiProviderResponse setEnabled(Long providerId, boolean enabled) {
        AiProvider provider = findActive(providerId);
        provider.setEnabled(enabled);
        return AiProviderResponse.from(providerRepository.save(provider));
    }

    /**
     * 지원 모델/모드 목록을 갱신한다. 목록이 자주 바뀌므로 코드가 아니라 데이터로 두고 화면에서 편집한다.
     * capabilities 의 다른 키는 보존한다.
     */
    @Transactional
    public AiProviderResponse updateCapabilities(Long providerId, ProviderCapabilities request) {
        AiProvider provider = findActive(providerId);
        Map<String, Object> capabilities = provider.getCapabilities() == null
                ? new HashMap<>() : new HashMap<>(provider.getCapabilities());
        capabilities.put("models", normalize(request.models()));
        capabilities.put("modes", normalize(request.modes()));
        if (request.notes() == null || request.notes().isBlank()) {
            capabilities.remove("notes");
        } else {
            capabilities.put("notes", request.notes().trim());
        }
        provider.setCapabilities(capabilities);
        return AiProviderResponse.from(providerRepository.save(provider));
    }

    private List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private AiProvider findActive(Long id) {
        return providerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(id)));
    }
}
