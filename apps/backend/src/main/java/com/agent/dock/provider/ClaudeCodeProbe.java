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

        try (InputStream input = process.getInputStream()) {
            String output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return ProbeResult.failure("확인 시간 초과 (" + TIMEOUT_SECONDS + "초)");
            }
            int exitCode = process.exitValue();
            String trimmed = output.trim();
            if (exitCode == 0) {
                return ProbeResult.success(trimmed.isEmpty() ? "OK" : shorten(trimmed));
            }
            return ProbeResult.failure(shorten(trimmed.isEmpty() ? "CLI 종료 코드 " + exitCode : trimmed));
        } catch (Exception ex) {
            process.destroyForcibly();
            return ProbeResult.failure("확인 중 오류: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private String shorten(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= DETAIL_LIMIT ? oneLine : oneLine.substring(0, DETAIL_LIMIT) + "...";
    }
}
