package com.agent.dock.provider;

import org.springframework.stereotype.Component;

/** Claude Code CLI 의 실행 파일 이름. probe/runtime/로그인 명령과 같은 변수(CLAUDE_CODE_BIN)를 쓴다. */
@Component
public class ClaudeCodeCli implements AiRuntimeCli {

    @Override
    public ProviderKey providerKey() {
        return ProviderKey.CLAUDE_CODE;
    }

    @Override
    public String binary() {
        return System.getenv().getOrDefault("CLAUDE_CODE_BIN", "claude");
    }
}
