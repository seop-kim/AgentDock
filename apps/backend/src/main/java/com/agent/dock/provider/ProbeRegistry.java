package com.agent.dock.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** provider key → probe 구현체 매핑. RuntimeRegistry 와 같은 방식. */
@Component
public class ProbeRegistry {
    private final Map<ProviderKey, AiRuntimeProbe> probes;

    public ProbeRegistry(List<AiRuntimeProbe> probeBeans) {
        this.probes = probeBeans.stream()
                .collect(Collectors.toMap(AiRuntimeProbe::providerKey, probe -> probe));
    }

    public Optional<AiRuntimeProbe> find(ProviderKey providerKey) {
        return Optional.ofNullable(probes.get(providerKey));
    }
}
