package com.agent.dock.provider.domain;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProviderKeyTest {

    @Test
    void everyKeyProvidesDisplayNameAndSeedDefaults() {
        for (ProviderKey key : ProviderKey.values()) {
            assertThat(key.displayName()).isNotBlank();
            Map<String, Object> capabilities = key.defaultCapabilities();
            assertThat(capabilities).containsKeys(
                    "models", "modes", "notes", "installRequire", "installPrerequisite", "install");
            assertThat(capabilities.get("installRequire")).isEqualTo("npm");
            assertThat((List<?>) capabilities.get("install")).isNotEmpty();
        }
    }

    @Test
    void claudeCodeDefaultsMatchSeed() {
        Map<String, Object> capabilities = ProviderKey.CLAUDE_CODE.defaultCapabilities();

        assertThat(capabilities.get("models")).isEqualTo(List.of("opus", "sonnet", "fable"));
        assertThat(capabilities.get("install")).isEqualTo(List.of("npm install -g @anthropic-ai/claude-code"));
    }
}
