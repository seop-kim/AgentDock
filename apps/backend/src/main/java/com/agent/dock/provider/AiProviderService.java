package com.agent.dock.provider;

import com.agent.dock.agent.AgentRepository;
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
    private final AiConnectionRepository connectionRepository;
    private final AgentRepository agentRepository;

    /** 논리 삭제되지 않은 Provider 만 반환한다. */
    public List<AiProviderResponse> findAll() {
        return providerRepository.findByDeletedAtIsNullOrderByNameAsc().stream().map(AiProviderResponse::from).toList();
    }

    /** Provider 를 만들고 Connection(Provider 당 1개)을 함께 만든다. */
    @Transactional
    public AiProviderResponse create(CreateAiProviderRequest request) {
        if (providerRepository.existsByKeyAndDeletedAtIsNull(request.key())) {
            throw new ConflictException("Provider already registered: " + request.key());
        }
        AiProvider provider = new AiProvider();
        provider.setKey(request.key());
        provider.setName(request.name());
        provider.setCapabilities(request.capabilities() != null ? request.capabilities() : new HashMap<>());
        AiProvider saved = providerRepository.save(provider);

        AiConnection connection = new AiConnection();
        connection.setProvider(saved);
        saved.setConnections(List.of(connectionRepository.save(connection)));
        return AiProviderResponse.from(saved);
    }

    /**
     * 논리 삭제. Agent 는 그대로 두고(사용 불가가 된다), Connection 은 지운다.
     * Connection 을 지우기 전에 Agent 의 connection_id 를 먼저 끊어 FK 위반을 피한다.
     */
    @Transactional
    public void delete(Long id) {
        AiProvider provider = findActive(id);
        agentRepository.detachConnectionsOfProvider(id);
        connectionRepository.deleteByProviderId(id);
        provider.setDeletedAt(Instant.now());
        providerRepository.save(provider);
    }

    /** 삭제된 Connection 을 다시 만든다("연결 추가"). Provider 당 1개만 허용한다. */
    public AiConnectionResponse createProviderConnection(Long providerId) {
        AiProvider provider = findActive(providerId);
        if (connectionRepository.existsByProviderId(providerId)) {
            throw new ConflictException("AiProvider %d already has a connection".formatted(providerId));
        }
        AiConnection connection = new AiConnection();
        connection.setProvider(provider);
        return AiConnectionResponse.from(connectionRepository.save(connection));
    }

    public AiConnectionResponse createConnection(CreateAiConnectionRequest request) {
        AiProvider provider = findActive(request.providerId());
        if (connectionRepository.existsByProviderId(request.providerId())) {
            throw new ConflictException("AiProvider %d already has a connection".formatted(request.providerId()));
        }
        AiConnection connection = new AiConnection();
        connection.setProvider(provider);
        connection.setAccountName(request.accountName());
        connection.setCredentialReference(request.credentialReference());
        return AiConnectionResponse.from(connectionRepository.save(connection));
    }

    public List<AiConnectionResponse> listConnections(Long providerId) {
        return connectionRepository.findByProviderId(providerId).stream().map(AiConnectionResponse::from).toList();
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
