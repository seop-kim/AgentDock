package com.agent.dock.provider.login;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 로그인 CLI 프로세스와의 입출력 경계. 지금은 파이프 구현(PipeLoginProcess)만 있고,
 * 메뉴형 CLI 가 필요해지면 PTY 구현으로 교체한다. 리스너는 start 전에 등록한다.
 */
public interface LoginProcess {
    void start(List<String> command) throws IOException;

    void onOutput(Consumer<String> listener);

    void onExit(IntConsumer listener);

    void write(String text) throws IOException;

    void close();
}
