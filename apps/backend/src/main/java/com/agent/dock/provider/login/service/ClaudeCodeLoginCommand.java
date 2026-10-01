package com.agent.dock.provider.login.service;

import com.agent.dock.provider.domain.ProviderKey;
import com.agent.dock.provider.login.interfaces.AiLoginCommand;
import java.util.List;
import org.springframework.stereotype.Component;

/** `claude auth login`. 바이너리는 CLAUDE_CODE_BIN 으로 override 한다(기본 claude). */
@Component
public class ClaudeCodeLoginCommand implements AiLoginCommand {

    @Override
    public ProviderKey providerKey() {
        return ProviderKey.CLAUDE_CODE;
    }

    @Override
    public List<String> command() {
        String binary = System.getenv().getOrDefault("CLAUDE_CODE_BIN", "claude");
        return List.of(binary, "auth", "login");
    }
}
