package com.agent.dock.runtime;

import com.agent.dock.common.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * provider key -> AgentRuntime 구현체 매핑.
 * 새 Runtime(Codex, Gemini 등)을 추가할 때는 AgentRuntime을 구현하고 @Component로 등록만 하면 되고,
 * 나머지 코드는 건드리지 않는다.
 */
@Component
public class RuntimeRegistry {
    private final Map<String, AgentRuntime> runtimes;

    public RuntimeRegistry(List<AgentRuntime> runtimeBeans) {
        this.runtimes = runtimeBeans.stream()
                .collect(Collectors.toMap(AgentRuntime::getProviderKey, r -> r));
    }

    public AgentRuntime resolve(String providerKey) {
        AgentRuntime runtime = runtimes.get(providerKey);
        if (runtime == null) {
            throw new NotFoundException("No AgentRuntime registered for provider \"" + providerKey + "\"");
        }
        return runtime;
    }
}
