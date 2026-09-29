package com.agent.dock.provider.login;

import com.agent.dock.provider.ProviderKey;
import org.springframework.stereotype.Component;

import java.util.List;

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
