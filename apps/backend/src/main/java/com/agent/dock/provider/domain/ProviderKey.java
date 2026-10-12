package com.agent.dock.provider.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 지원하는 AI 런타임 키. 표시 이름과 기본 capabilities(모델/모드/설치 계획)를 여기서 제공한다 —
 * 카탈로그(GET /ai-providers/catalog)와 capabilities reset 이 같은 값을 쓴다(시드 V4·V5·V9 와 같다).
 */
public enum ProviderKey {
    CLAUDE_CODE("Claude Code",
            List.of("opus", "sonnet", "fable"),
            List.of("acceptEdits", "auto", "bypassPermissions", "manual", "dontAsk", "plan"),
            "models 는 `claude --help` 에 나온 alias 예시이며 CLI 버전에 따라 바뀐다. 화면에서 편집한다.",
            "npm install -g @anthropic-ai/claude-code"),
    CODEX("Codex", List.of(), List.of(),
            "CLI 가 이 머신에 없어 미확인. 화면에서 입력한다.", "npm install -g @openai/codex"),
    COMMAND_CODE("Command Code", List.of(), List.of(),
            "CLI 가 이 머신에 없어 미확인. 화면에서 입력한다.", "npm install -g command-code"),
    GEMINI("Gemini", List.of(), List.of(),
            "CLI 가 이 머신에 없어 미확인. 화면에서 입력한다.", "npm install -g @google/gemini-cli");
    private final String displayName;
    private final List<String> defaultModels;
    private final List<String> defaultModes;
    private final String defaultNotes;
    private final String installCommand;

    ProviderKey(String displayName, List<String> defaultModels, List<String> defaultModes,
                String defaultNotes, String installCommand) {
        this.displayName = displayName;
        this.defaultModels = defaultModels;
        this.defaultModes = defaultModes;
        this.defaultNotes = defaultNotes;
        this.installCommand = installCommand;
    }

    public String displayName() {
        return displayName;
    }

    /** 런타임 등록·capabilities 기본값 reset 이 쓰는 기본값(시드와 같다). */
    public Map<String, Object> defaultCapabilities() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("models", List.copyOf(defaultModels));
        capabilities.put("modes", List.copyOf(defaultModes));
        capabilities.put("notes", defaultNotes);
        capabilities.put("installRequire", "npm");
        capabilities.put("installPrerequisite", List.of("nvm install lts", "nvm use lts"));
        capabilities.put("install", List.of(installCommand));
        return capabilities;
    }
}
