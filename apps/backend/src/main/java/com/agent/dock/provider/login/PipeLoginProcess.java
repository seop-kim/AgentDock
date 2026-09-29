package com.agent.dock.provider.login;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** ProcessBuilder(shell 미경유) 기반 구현. stderr 를 stdout 에 합쳐 읽는다. */
public class PipeLoginProcess implements LoginProcess {
    private Consumer<String> outputListener = text -> { };
    private IntConsumer exitListener = code -> { };
    private Process process;

    @Override
    public void start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        process = builder.start();

        Thread reader = new Thread(this::pump, "login-output");
        reader.setDaemon(true);
        reader.start();

        // 로그인 CLI 가 띄운 브라우저 등 자식 프로세스가 파이프를 잡고 있으면 EOF 가 오지 않을 수 있으므로,
        // 종료 감지는 프로세스 종료 이벤트로 하고 남은 출력은 잠깐만 기다린다.
        process.onExit().thenAccept(finished -> {
            try {
                reader.join(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exitListener.accept(finished.exitValue());
        });
    }

    private void pump() {
        try (Reader in = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                outputListener.accept(new String(buffer, 0, read));
            }
        } catch (IOException ignored) {
            // 프로세스를 종료하면 스트림이 닫힌다
        }
    }

    @Override
    public void onOutput(Consumer<String> listener) {
        this.outputListener = listener;
    }

    @Override
    public void onExit(IntConsumer listener) {
        this.exitListener = listener;
    }

    @Override
    public void write(String text) throws IOException {
        OutputStream out = process.getOutputStream();
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public void close() {
        if (process != null) {
            process.destroyForcibly();
        }
    }
}
