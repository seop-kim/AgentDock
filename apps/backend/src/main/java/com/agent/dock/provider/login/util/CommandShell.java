package com.agent.dock.provider.login.util;

/**
 * 설치 명령을 플랫폼 셸로 감싼다.
 * Java 의 ProcessBuilder 는 `npm` 처럼 실체가 .cmd/.ps1 인 실행 파일을 직접 실행하지 못한다
 * (CreateProcess error=2). 그래서 설치처럼 셸 기능이 필요한 명령은 셸을 통해 실행한다.
 * 에이전트 실행(`ProcessService`)은 프롬프트가 흐르므로 셸을 경유하지 않는다 — 이 클래스는 설치 세션 전용이다.
 */
public final class CommandShell {

    private CommandShell() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** 명령 문자열 하나를 셸 실행용 인자 목록으로 감싼다. */
    public static java.util.List<String> wrap(String command) {
        return isWindows()
                ? java.util.List.of("cmd.exe", "/c", command)
                : java.util.List.of("sh", "-c", command);
    }

}
