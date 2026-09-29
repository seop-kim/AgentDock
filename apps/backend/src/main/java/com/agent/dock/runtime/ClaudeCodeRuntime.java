package com.agent.dock.runtime;

import com.agent.dock.process.ProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Claude Code CLI를 비대화형(print) 모드로 실행하는 Runtime 구현체.
 * 실행 바이너리는 환경변수 CLAUDE_CODE_BIN으로 override 가능하다(기본값 claude).
 */
@Component
@RequiredArgsConstructor
public class ClaudeCodeRuntime implements AgentRuntime {
    private final ProcessService processService;

    @Override
    public String getProviderKey() {
        return "CLAUDE_CODE";
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionRequest request) throws Exception {
        String command = System.getenv().getOrDefault("CLAUDE_CODE_BIN", "claude");
        Process process = processService.spawn(request.executionId(), command, buildArgs(request), request.workspacePath());

        CompletableFuture<Void> stdout = CompletableFuture.runAsync(() -> pump(process.getInputStream(), "stdout", request));
        CompletableFuture<Void> stderr = CompletableFuture.runAsync(() -> pump(process.getErrorStream(), "stderr", request));

        int exitCode = process.waitFor();
        CompletableFuture.allOf(stdout, stderr).join();
        return new AgentExecutionResult(exitCode);
    }

    /**
     * CLI 인자 조립. Agent 의 mode 는 Claude Code 의 권한/실행 모드로 전달한다.
     * 값이 없으면 플래그를 붙이지 않고 CLI 기본값을 쓴다.
     */
    List<String> buildArgs(AgentExecutionRequest request) {
        List<String> args = new ArrayList<>(List.of("-p", request.prompt(), "--output-format", "text"));
        if (request.model() != null && !request.model().isBlank()) {
            args.add("--model");
            args.add(request.model());
        }
        if (request.mode() != null && !request.mode().isBlank()) {
            args.add("--permission-mode");
            args.add(request.mode());
        }
        return args;
    }

    private void pump(InputStream input, String stream, AgentExecutionRequest request) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                request.onLog().accept(line + "\n", stream);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void cancel(String executionId) {
        processService.cancel(executionId);
    }
}
