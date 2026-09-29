package com.agent.dock.provider;

import java.util.List;

/**
 * Provider 가 지원하는 모델/모드 목록. 목록은 코드가 아니라 데이터이며 화면에서 편집한다.
 * 값이 비어 있어도 실행을 막지 않는다(최종 판단은 CLI).
 */
public record ProviderCapabilities(List<String> models, List<String> modes, String notes) {
}
