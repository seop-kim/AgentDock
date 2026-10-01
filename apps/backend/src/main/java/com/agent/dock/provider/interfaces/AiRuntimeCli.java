package com.agent.dock.provider.interfaces;

import com.agent.dock.provider.domain.ProviderKey;

/**
 * 런타임 CLI 의 실행 파일 이름을 알려 주는 경계. probe/로그인 명령과 같은 바이너리 규칙을 쓴다.
 * 새 런타임을 지원할 때는 이 인터페이스를 구현하고 @Component 로 등록하기만 하면 CLI 상태 조회에 자동으로 잡힌다.
 */
public interface AiRuntimeCli {
    ProviderKey providerKey();

    /** 실행 파일 이름(환경변수 override 반영). 예: claude. */
    String binary();
}
