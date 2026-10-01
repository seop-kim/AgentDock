package com.agent.dock.provider.service;

import com.agent.dock.provider.domain.ProviderKey;
import com.agent.dock.provider.interfaces.AiRuntimeCli;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** provider key → CLI 실행 파일 매핑. ProbeRegistry/RuntimeRegistry 와 같은 방식. */
@Component
public class CliRegistry {
    private final Map<ProviderKey, AiRuntimeCli> clis;

    public CliRegistry(List<AiRuntimeCli> cliBeans) {
        this.clis = cliBeans.stream()
                .collect(Collectors.toMap(AiRuntimeCli::providerKey, cli -> cli));
    }

    public Optional<AiRuntimeCli> find(ProviderKey providerKey) {
        return Optional.ofNullable(clis.get(providerKey));
    }
}
