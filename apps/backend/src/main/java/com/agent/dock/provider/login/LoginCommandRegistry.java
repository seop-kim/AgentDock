package com.agent.dock.provider.login;

import com.agent.dock.provider.ProviderKey;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** provider key → 로그인 명령 매핑. ProbeRegistry 와 같은 방식. */
@Component
public class LoginCommandRegistry {
    private final Map<ProviderKey, AiLoginCommand> commands;

    public LoginCommandRegistry(List<AiLoginCommand> commandBeans) {
        this.commands = commandBeans.stream()
                .collect(Collectors.toMap(AiLoginCommand::providerKey, command -> command));
    }

    public Optional<AiLoginCommand> find(ProviderKey providerKey) {
        return Optional.ofNullable(commands.get(providerKey));
    }
}
