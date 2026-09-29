package com.agent.dock.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Claude Code CLI 가 실제로 인증되어 응답하는지 확인한다.
 * 바이너리는 환경변수 CLAUDE_CODE_BIN 으로 override 가능(기본값 claude).
 * CLI 자체의 로그인 세션을 그대로 사용하므로 이 앱은 자격증명을 다루지 않는다.
 */
@Component
@Slf4j
public class ClaudeCodeProbe implements AiConnectionProbe {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int DETAIL_LIMIT = 500;
    private static final String PROBE_PROMPT = "Reply with exactly OK and nothing else.";

    @Override
    public ProviderKey providerKey() {
        return ProviderKey.CLAUDE_CODE;
    }

    @Override
    public ProbeResult check() {
        String command = System.getenv().getOrDefault("CLAUDE_CODE_BIN", "claude");
        ProcessBuilder builder = new ProcessBuilder(List.of(command, "-p", PROBE_PROMPT, "--output-format", "text"));
        builder.redirectErrorStream(true);
        builder.directory(null);

        Process process;
        try {
            process = builder.start();
        } catch (Exception ex) {
            return ProbeResult.failure("CLI 실행 실패: " + command + " 를 찾을 수 없습니다 (CLAUDE_CODE_BIN 으로 경로 지정 가능)");
        }

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
            int exitCode = process.exitValue();
            String output = collected.toString().trim();
            if (exitCode == 0) {
                return ProbeResult.success(output.isEmpty() ? "OK" : shorten(output));
            }
            return ProbeResult.failure(shorten(output.isEmpty() ? "CLI 종료 코드 " + exitCode : output));
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return ProbeResult.failure("확인 중 인터럽트되었습니다");
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

    private String shorten(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= DETAIL_LIMIT ? oneLine : oneLine.substring(0, DETAIL_LIMIT) + "...";
    }
}
