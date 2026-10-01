package com.agent.dock.provider.service;

import com.agent.dock.provider.domain.ProviderKey;
import com.agent.dock.provider.interfaces.AiRuntimeCli;
import org.springframework.stereotype.Component;

/** Command Code CLI 의 실행 파일 이름. 사용자 확인: 설치 명령은 npm i -g command-code@latest, 실행 파일은 cmdc. */
@Component
public class CommandCodeCli implements AiRuntimeCli {

    @Override
    public ProviderKey providerKey() {
        return ProviderKey.COMMAND_CODE;
    }

    @Override
    public String binary() {
        return System.getenv().getOrDefault("COMMAND_CODE_BIN", "cmdc");
    }
}
