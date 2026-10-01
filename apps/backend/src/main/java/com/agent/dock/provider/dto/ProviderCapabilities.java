package com.agent.dock.provider.dto;

import java.util.List;

/**
 * 런타임별 지원 능력. 목록은 코드가 아니라 데이터이며 화면에서 편집한다.
 * install 계열은 CLI 설치 계획이다.
 *  - installRequire: 먼저 있어야 하는 실행 파일(예: npm). 없으면 installPrerequisite 를 먼저 실행한다.
 *  - installPrerequisite: 필수 실행 파일이 없을 때 실행할 단계들(예: nvm install lts).
 *  - install: 실제 설치 단계들.
 */
public record ProviderCapabilities(
        List<String> models,
        List<String> modes,
        String notes,
        List<String> install,
        String installRequire,
        List<String> installPrerequisite
) {
}
