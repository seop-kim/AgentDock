package com.agent.dock.runtime.service;

import com.agent.dock.process.service.ProcessService;
import com.agent.dock.runtime.dto.AgentExecutionRequest;
import com.agent.dock.runtime.dto.AgentExecutionResult;
import com.agent.dock.runtime.dto.ExecutionMetrics;
import com.agent.dock.runtime.interfaces.AgentRuntime;
import com.agent.dock.runtime.util.ClaudeStreamJson;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Claude Code CLI 를 비대화형(print) 모드로 실행하는 Runtime 구현체.
 * 실행 바이너리는 환경변수 CLAUDE_CODE_BIN 으로 override 가능하다(기본값 claude).
 *
 * 출력은 `--output-format stream-json`(JSONL)으로 받아 사람이 읽을 로그 줄로 바꾸고,
 * 마지막 result 이벤트에서 결과 텍스트와 계측값(토큰·비용·시간·세션)을 꺼낸다.
 * 판단 실행이면 `--json-schema` 로 계약(delegate|done)을 강제한다(스키마는 호출부가 만든다).
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

        // 프롬프트는 인자가 아니라 표준입력(UTF-8)으로 넘긴다.
        // 길이 제한과 인용 문제를 피할 수 있고, 한글도 그대로 전달된다(2026-09-30 실측).
        writePrompt(process, request.prompt());

        AtomicReference<ClaudeStreamJson.Outcome> outcome = new AtomicReference<>();
        CompletableFuture<Void> stdout = CompletableFuture.runAsync(
                () -> pumpStdout(process.getInputStream(), request, outcome));
        CompletableFuture<Void> stderr = CompletableFuture.runAsync(
                () -> pumpRaw(process.getErrorStream(), "stderr", request));

        int exitCode = process.waitFor();
        CompletableFuture.allOf(stdout, stderr).join();

        ClaudeStreamJson.Outcome parsed = outcome.get();
        if (parsed == null) {
            return new AgentExecutionResult(exitCode, null, null, ExecutionMetrics.empty(), exitCode != 0);
        }
        return new AgentExecutionResult(exitCode, parsed.resultText(), parsed.structured(), parsed.metrics(),
                parsed.isError() || exitCode != 0);
    }

    private void writePrompt(Process process, String prompt) {
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException ignored) {
            // 이미 닫혔으면 더 쓸 것이 없다.
        }
    }

    /**
     * CLI 인자 조립. 프롬프트는 표준입력으로 넘기므로 `-p` 뒤에 값을 붙이지 않는다.
     * - systemPrompt 는 프롬프트 계층(마스터 → 그룹 → 에이전트)을 합친 값으로, 기본 시스템 프롬프트에 덧붙인다.
     * - mode 는 권한/실행 모드(--permission-mode), model 은 --model.
     * - maxBudgetUsd 는 스텝 예산 상한(--max-budget-usd).
     * 값이 없으면 플래그를 붙이지 않고 CLI 기본값을 쓴다.
     *
     * 출력 계약(JSON)은 `--json-schema` 로 강제하지 않는다. Java 의 Windows 인자 인용 때문에
     * 따옴표가 든 JSON 문자열이 CLI 에 온전히 전달되지 않는다(2026-09-30 실측: 파일 경로도 거부).
     * 대신 프롬프트에 스키마를 적어 주고, 결과 텍스트에서 JSON 을 파싱한다(ClaudeStreamJson).
     */
    List<String> buildArgs(AgentExecutionRequest request) {
        List<String> args = new ArrayList<>(List.of("-p", "--output-format", "stream-json", "--verbose"));
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            args.add("--append-system-prompt");
            args.add(request.systemPrompt());
        }
        if (request.model() != null && !request.model().isBlank()) {
            args.add("--model");
            args.add(request.model());
        }
        if (request.mode() != null && !request.mode().isBlank()) {
            args.add("--permission-mode");
            args.add(request.mode());
        }
        if (request.maxBudgetUsd() != null) {
            args.add("--max-budget-usd");
            args.add(request.maxBudgetUsd().toPlainString());
        }
        return args;
    }

    private void pumpStdout(InputStream input, AgentExecutionRequest request, AtomicReference<ClaudeStreamJson.Outcome> outcome) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ClaudeStreamJson.outcome(line).ifPresent(outcome::set);
                for (ClaudeStreamJson.LogLine logLine : ClaudeStreamJson.render(line)) {
                    request.onLog().accept(logLine.content(), logLine.stream());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void pumpRaw(InputStream input, String stream, AgentExecutionRequest request) {
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
