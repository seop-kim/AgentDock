package com.agent.dock.provider.login;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 런타임 CLI 명령 세션과의 입출력 경계. 여러 단계(설치 선행 도구 → 본 설치)를 순서대로 실행할 수 있다.
 * 리스너는 start 전에 등록한다.
 */
public interface LoginProcess {
    /** 각 원소가 하나의 실행 단계(명령 + 인자)다. 앞 단계가 실패하면 다음 단계는 실행하지 않는다. */
    void start(List<List<String>> commands) throws IOException;

    void onOutput(Consumer<String> listener);

    void onExit(IntConsumer listener);

    void write(String text) throws IOException;

    void close();
}
