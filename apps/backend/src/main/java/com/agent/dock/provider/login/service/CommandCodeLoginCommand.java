package com.agent.dock.provider.login.service;

import com.agent.dock.provider.domain.ProviderKey;
import com.agent.dock.provider.login.interfaces.AiLoginCommand;
import org.springframework.stereotype.Component;

import java.util.List;

/** `cmdc login`. 바이너리는 COMMAND_CODE_BIN 으로 override 한다(기본 cmdc). 사용자 확인. */
@Component
public class CommandCodeLoginCommand implements AiLoginCommand {

    @Override
    public ProviderKey providerKey() {
        return ProviderKey.COMMAND_CODE;
    }

    @Override
    public List<String> command() {
        String binary = System.getenv().getOrDefault("COMMAND_CODE_BIN", "cmdc");
        return List.of(binary, "login");
    }
}
