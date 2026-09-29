package com.agent.dock.provider;

/**
 * 런타임별 "이 폴더에서 지금 실제로 쓸 수 있는 상태인가"를 확인하는 경계.
 * 새 런타임을 지원할 때는 이 인터페이스를 구현하고 @Component 로 등록하기만 하면 된다.
 */
public interface AiConnectionProbe {
    ProviderKey providerKey();

    /** cwd 를 작업 디렉터리로 CLI 를 한 번 실행해 응답 여부를 본다. cwd 가 null 이면 현재 프로세스의 디렉터리를 쓴다. */
    ProbeResult check(String cwd);
}
