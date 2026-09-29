package com.agent.dock.provider;

/**
 * Provider 별 "이 CLI가 지금 실제로 쓸 수 있는 상태인가"를 확인하는 경계.
 * 새 Provider를 지원할 때는 이 인터페이스를 구현하고 @Component로 등록하기만 하면 된다.
 */
public interface AiConnectionProbe {
    ProviderKey providerKey();

    ProbeResult check();
}
