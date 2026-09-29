package com.agent.dock.provider;

import com.agent.dock.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiProviderService {
    private final AiProviderRepository providerRepository;
    private final AiConnectionRepository connectionRepository;

    public List<AiProviderResponse> findAll() {
        return providerRepository.findAllByOrderByNameAsc().stream().map(AiProviderResponse::from).toList();
    }

    public AiProviderResponse create(CreateAiProviderRequest request) {
        AiProvider provider = new AiProvider();
        provider.setKey(request.key());
        provider.setName(request.name());
        provider.setCapabilities(request.capabilities() != null ? request.capabilities() : new HashMap<>());
        return AiProviderResponse.from(providerRepository.save(provider));
    }

    public AiConnectionResponse createConnection(CreateAiConnectionRequest request) {
        AiProvider provider = providerRepository.findById(request.providerId())
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(request.providerId())));
        AiConnection connection = new AiConnection();
        connection.setProvider(provider);
        connection.setAccountName(request.accountName());
        connection.setCredentialReference(request.credentialReference());
        return AiConnectionResponse.from(connectionRepository.save(connection));
    }

    public List<AiConnectionResponse> listConnections(Long providerId) {
        return connectionRepository.findByProviderId(providerId).stream().map(AiConnectionResponse::from).toList();
    }
}
