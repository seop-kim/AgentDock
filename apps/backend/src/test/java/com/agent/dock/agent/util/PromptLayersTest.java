package com.agent.dock.agent.util;

import com.agent.dock.agent.domain.Agent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PromptLayersTest {

    @Test
    void layersAreJoinedInOrder() {
        String combined = PromptLayers.combine("마스터 규칙", "그룹 규칙", "내 역할");

        assertThat(combined).isEqualTo("마스터 규칙\n\n그룹 규칙\n\n내 역할");
    }

    @Test
    void blankAndNullLayersAreSkipped() {
        assertThat(PromptLayers.combine("", "  ", null)).isEmpty();
        assertThat(PromptLayers.combine("마스터", "", "내 역할")).isEqualTo("마스터\n\n내 역할");
        assertThat(PromptLayers.combine(null, null, "내 역할")).isEqualTo("내 역할");
    }

    @Test
    void layersAreTrimmed() {
        assertThat(PromptLayers.combine("  마스터  ", "\n그룹\n", "역할 ")).isEqualTo("마스터\n\n그룹\n\n역할");
    }
}
