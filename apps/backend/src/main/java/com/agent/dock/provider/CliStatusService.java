package com.agent.dock.provider;

import com.agent.dock.common.NotFoundException;
import com.agent.dock.process.Executables;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 런타임 CLI 의 실행 파일 경로/버전/실행 가능 여부를 확인한다.
 * 프로세스를 실행하므로 @Transactional 을 붙이지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CliStatusService {

    private static final int VERSION_TIMEOUT_SECONDS = 10;

    private final AiProviderRepository providerRepository;
    private final CliRegistry cliRegistry;

    public CliStatusResponse check(Long providerId) {
        AiProvider provider = providerRepository.findByIdAndDeletedAtIsNull(providerId)
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(providerId)));

        AiRuntimeCli cli = cliRegistry.find(provider.getKey()).orElse(null);
        if (cli == null) {
            return new CliStatusResponse(null, null, false,
                    "이 런타임은 아직 CLI 정보를 확인할 수 없습니다: " + provider.getKey());
        }

        String binary = cli.binary();
        String resolved = Executables.locate(binary);
        if (resolved == null) {
            return new CliStatusResponse(null, null, false, "실행 파일을 찾을 수 없습니다: " + binary);
        }

        String version = readVersion(resolved);
        return new CliStatusResponse(resolved, version, true,
                version == null ? "버전을 확인하지 못했습니다" : null);
    }

    /** `<실행 파일> --version` 을 짧게 실행해 첫 줄을 읽는다. 셸은 경유하지 않는다. */
    private String readVersion(String executable) {
        ProcessBuilder builder = new ProcessBuilder(List.of(executable, "--version"));
        builder.redirectErrorStream(true);

        Process process;
        try {
            process = builder.start();
        } catch (Exception ex) {
            return null;
        }

        // 출력은 별도 스레드에서 읽는다(출력이 닫히지 않아도 타임아웃이 동작하도록).
        StringBuffer collected = new StringBuffer();
        Thread reader = new Thread(() -> pump(process.getInputStream(), collected), "cli-version-output");
        reader.setDaemon(true);
        reader.start();

        try {
            if (!process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            reader.join(1000);
            String output = collected.toString().trim();
            String firstLine = output.lines().findFirst().orElse("").trim();
            return firstLine.isEmpty() ? null : firstLine;
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return null;
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
}
