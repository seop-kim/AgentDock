package com.agent.dock.provider.login.service;

import com.agent.dock.provider.login.interfaces.LoginProcess;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.List;

/** ProcessBuilder(shell 미경유) 기반 구현. stderr 를 stdout 에 합쳐 읽고, 여러 단계를 순서대로 실행한다. */
public class PipeLoginProcess implements LoginProcess {
    private Consumer<String> outputListener = text -> { };
    private IntConsumer exitListener = code -> { };
    private volatile Process process;

    @Override
    public void start(List<List<String>> commands) throws IOException {
        Thread runner = new Thread(() -> runSteps(commands), "command-steps");
        runner.setDaemon(true);
        runner.start();
    }

    private void runSteps(List<List<String>> commands) {
        int exitCode = 0;
        for (List<String> command : commands) {
            try {
                process = new ProcessBuilder(command).redirectErrorStream(true).start();
            } catch (IOException ex) {
                outputListener.accept("실행 실패: " + String.join(" ", command) + " — " + ex.getMessage() + "\n");
                exitListener.accept(-1);
                return;
            }
            pump(process.getInputStream());
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                exitListener.accept(-1);
                return;
            }
            outputListener.accept("\n[종료 코드 " + exitCode + "] " + String.join(" ", command) + "\n");
            if (exitCode != 0) {
                exitListener.accept(exitCode);
                return;
            }
        }
        exitListener.accept(exitCode);
    }

    private void pump(java.io.InputStream input) {
        try (Reader in = new InputStreamReader(input, StandardCharsets.UTF_8)) {
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
        Process current = process;
        if (current == null) {
            throw new IOException("실행 중인 단계가 없습니다");
        }
        OutputStream out = current.getOutputStream();
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public void close() {
        Process current = process;
        if (current != null) {
            current.destroyForcibly();
        }
    }
}
