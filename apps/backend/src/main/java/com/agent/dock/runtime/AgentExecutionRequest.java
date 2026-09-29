package com.agent.dock.runtime;

import java.util.function.BiConsumer;

public record AgentExecutionRequest(
        String executionId,
        String prompt,
        String workspacePath,
        String model,
        String mode,
        BiConsumer<String, String> onLog
) {}
