package com.agent.dock.provider.service;

import com.agent.dock.process.util.Executables;
import com.agent.dock.provider.domain.ProviderKey;
import com.agent.dock.provider.dto.ProbeResult;
import com.agent.dock.provider.interfaces.AiRuntimeProbe;
import com.agent.dock.runtime.util.CommandCodeStreamJson;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Command Code CLI(`cmdc`)가 실제로 인증되어 응답하는지 확인한다.
 * 바이너리는 환경변수 COMMAND_CODE_BIN 으로 override 가능(기본값 cmdc).
 * CLI 자체의 로그인 세션을 그대로 사용하므로 이 앱은 자격증명을 다루지 않는다.
 *
 * `-p --output-format json` 으로 짧은 프롬프트를 실행한다(프롬프트는 표준입력 UTF-8).
 * `-t` 로 프로젝트 신뢰를 자동 승인해, 처음 보는 폴더에서도 권한 프롬프트에서 멈추지 않게 한다.
 */
@Component
@Slf4j
public class CommandCodeProbe implements AiRuntimeProbe {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int DETAIL_LIMIT = 500;
    private static final String PROBE_PROMPT = "Reply with exactly OK and nothing else.";

    @Override
    public ProviderKey providerKey() {
        return ProviderKey.COMMAND_CODE;
    }

    @Override
    public ProbeResult check(String cwd) {
        // npm 으로 설치된 cmdc 는 cmdc.cmd 이므로 PATH/PATHEXT 로 실제 파일을 찾아 준다.
        String command = Executables.resolve(System.getenv().getOrDefault("COMMAND_CODE_BIN", "cmdc"));
        ProcessBuilder builder = new ProcessBuilder(List.of(command, "-p", "--output-format", "json", "-t"));
        builder.redirectErrorStream(true);
        builder.directory(cwd == null ? null : new File(cwd));

        Process process;
        try {
            process = builder.start();
        } catch (Exception ex) {
            // 실행 파일 자체가 없으면 화면에서 설치를 안내할 수 있게 cliMissing 으로 표시한다.
            return ProbeResult.cliMissing("CLI 실행 실패: " + command + " 를 찾을 수 없습니다 (에이전트 설정에서 설치하거나 COMMAND_CODE_BIN 으로 경로 지정)");
        }

        writePrompt(process, PROBE_PROMPT);

        // 출력은 별도 스레드에서 읽는다. 프로세스가 끝나지 않고 stdout 도 닫지 않으면
        // 출력을 먼저 다 읽는 방식은 영원히 블록되어 타임아웃이 동작하지 않는다.
        StringBuffer collected = new StringBuffer();
        Thread reader = new Thread(() -> pump(process.getInputStream(), collected), "probe-output");
        reader.setDaemon(true);
        reader.start();

        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return ProbeResult.failure("확인 시간 초과 (" + TIMEOUT_SECONDS + "초)");
            }
            // 종료 감지를 우선하고 남은 출력은 잠깐만 기다린다.
            reader.join(1000);
            return interpret(process.exitValue(), collected.toString());
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return ProbeResult.failure("확인 중 인터럽트되었습니다");
        }
    }

    /**
     * 종료 코드와 출력(NDJSON)을 확인 결과로 바꾼다.
     * result 줄의 finalText 를 사람이 읽을 문구로 쓰고, 없으면 출력(또는 종료 코드)을 그대로 보여 준다.
     */
    static ProbeResult interpret(int exitCode, String output) {
        String text = output == null ? "" : output.trim();
        String finalText = lastFinalText(text);
        if (exitCode != 0) {
            String detail = finalText != null ? finalText : (text.isEmpty() ? "CLI 종료 코드 " + exitCode : shorten(text));
            return ProbeResult.failure(shorten(detail));
        }
        String message = finalText != null ? finalText : (text.isEmpty() ? "OK" : shorten(text));
        return ProbeResult.success(shorten(message));
    }

    /** NDJSON 줄들 중 마지막 result 줄의 finalText(없으면 null). */
    private static String lastFinalText(String output) {
        return output.lines()
                .map(CommandCodeStreamJson::outcome)
                .flatMap(Optional::stream)
                .map(CommandCodeStreamJson.Outcome::resultText)
                .filter(value -> value != null && !value.isBlank())
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private static void writePrompt(Process process, String prompt) {
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException ignored) {
            // 이미 닫혔으면 더 쓸 것이 없다.
        }
    }

    private void pump(InputStream input, StringBuffer sink) {
        try (InputStream stream = input) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                sink.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // 프로세스를 종료하면 스트림이 닫힌다
        }
    }

    private static String shorten(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= DETAIL_LIMIT ? oneLine : oneLine.substring(0, DETAIL_LIMIT) + "...";
    }
}
