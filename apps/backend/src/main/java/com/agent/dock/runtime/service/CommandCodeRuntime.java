package com.agent.dock.runtime.service;

import com.agent.dock.process.service.ProcessService;
import com.agent.dock.runtime.dto.AgentExecutionRequest;
import com.agent.dock.runtime.dto.AgentExecutionResult;
import com.agent.dock.runtime.dto.ExecutionMetrics;
import com.agent.dock.runtime.interfaces.AgentRuntime;
import com.agent.dock.runtime.util.CommandCodeStreamJson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Command Code CLI(`cmdc`)를 비대화형(print) 모드로 실행하는 Runtime 구현체.
 * 실행 바이너리는 환경변수 COMMAND_CODE_BIN 으로 override 가능하다(기본값 cmdc).
 *
 * 출력은 `-p --output-format json`(NDJSON 이벤트 스트림 + 마지막 result 한 줄)으로 받아
 * 사람이 읽을 로그 줄로 바꾸고, 마지막 result 줄에서 결과 텍스트와 계측값(토큰·시간·세션)을 꺼낸다.
 * 프롬프트는 인자가 아니라 표준입력(UTF-8)으로 넘긴다(2026-10-01 실측: 파이프로 넘긴 문장이 그대로 처리됐다).
 */
@Component
@RequiredArgsConstructor
public class CommandCodeRuntime implements AgentRuntime {
    private final ProcessService processService;

    @Override
    public String getProviderKey() {
        return "COMMAND_CODE";
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionRequest request) throws Exception {
        String command = System.getenv().getOrDefault("COMMAND_CODE_BIN", "cmdc");
        Process process = processService.spawn(request.executionId(), command, buildArgs(request), request.workspacePath());

        // 프롬프트는 인자가 아니라 표준입력(UTF-8)으로 넘긴다.
        // `-p` 뒤에 값을 붙이지 않으면 cmdc 가 stdin 을 읽는다(2026-10-01 실측).
        writePrompt(process, request.prompt());

        AtomicReference<CommandCodeStreamJson.Outcome> outcome = new AtomicReference<>();
        CompletableFuture<Void> stdout = CompletableFuture.runAsync(
                () -> pumpStdout(process.getInputStream(), request, outcome), StreamPumps.pool());
        CompletableFuture<Void> stderr = CompletableFuture.runAsync(
                () -> pumpRaw(process.getErrorStream(), "stderr", request), StreamPumps.pool());

        int exitCode = process.waitFor();
        CompletableFuture.allOf(stdout, stderr).join();

        CommandCodeStreamJson.Outcome parsed = outcome.get();
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
     * <ul>
     *   <li>`-t`(프로젝트 자동 신뢰)는 항상 붙인다 — 무인 실행이 첫 권한 프롬프트에서 멈추지 않게.</li>
     *   <li>model 은 `--model`, mode 는 `--permission-mode`(standard|plan|accept-edits|yolo).</li>
     * </ul>
     * 값이 없으면 플래그를 붙이지 않고 CLI 기본값을 쓴다.
     *
     * Command Code 에는 확인된 시스템 프롬프트/예산 플래그가 없다(`cmdc --help` 기준).
     * 확인되지 않은 플래그를 붙이지 않는다(추측 금지) — 페르소나 계층은 프롬프트 본문으로만 전달된다.
     */
    List<String> buildArgs(AgentExecutionRequest request) {
        List<String> args = new ArrayList<>(List.of("-p", "--output-format", "json", "-t"));
        if (request.model() != null && !request.model().isBlank()) {
            args.add("--model");
            args.add(request.model());
        }
        if (request.mode() != null && !request.mode().isBlank()) {
            args.add("--permission-mode");
            args.add(request.mode());
        }
        // 세션 이어받기: 같은 실행의 다음 스텝은 자기 세션을 이어받아 앞 스텝의 맥락을 다시 설명하지 않는다.
        // cmdc 에는 분기(--fork-session) 플래그가 확인되지 않아(추측 금지) **자식은 새 세션**으로 시작한다 —
        // 자식에게 넘길 맥락은 지시 파일(.agentdock/prompts)과 그룹 공유 노트가 맡는다.
        if (!request.forkSession() && request.resumeSessionId() != null && !request.resumeSessionId().isBlank()) {
            args.add("--resume");
            args.add(request.resumeSessionId());
        }
        return args;
    }

    private void pumpStdout(InputStream input, AgentExecutionRequest request, AtomicReference<CommandCodeStreamJson.Outcome> outcome) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                CommandCodeStreamJson.outcome(line).ifPresent(outcome::set);
                for (CommandCodeStreamJson.LogLine logLine : CommandCodeStreamJson.render(line)) {
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
