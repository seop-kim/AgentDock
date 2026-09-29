package com.agent.dock.provider.login;

import com.agent.dock.provider.ProviderKey;

import java.util.List;

/**
 * Provider 별 로그인 명령. 서버 고정값이며 사용자 입력으로 만들지 않는다.
 * 새 Provider 를 지원할 때는 이 인터페이스를 구현하고 @Component 로 등록하기만 하면 된다.
 */
public interface AiLoginCommand {
    ProviderKey providerKey();

    List<String> command();
}
