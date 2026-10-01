package com.agent.dock.agent;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 프롬프트 계층(마스터 → 그룹 → 에이전트)을 하나의 시스템 프롬프트로 겹친다.
 * 목업에서 확정한 순서를 그대로 따르고, 빈 값은 건너뛰며, 각 층은 빈 줄로 구분한다.
 */
public final class PromptLayers {

    private PromptLayers() {
    }

    public static String combine(String masterPrompt, String groupPrompt, String agentPersona) {
        return Stream.of(masterPrompt, groupPrompt, agentPersona)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(layer -> !layer.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }
}
