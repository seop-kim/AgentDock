package com.agent.dock.process;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PATH/PATHEXT 로 실행 파일을 해석한다.
 * Java 의 ProcessBuilder 는 `npm` 처럼 확장자 없이 쓰는 이름을 npm.cmd 로 찾아 주지 못한다
 * (CreateProcess error=2). 그래서 PATH 를 직접 훑어 실제 파일(npm.cmd 등)을 찾아 준다.
 * 이렇게 하면 셸을 경유하지 않고도 npm/nvm/claude(.cmd) 를 실행할 수 있어, 프롬프트가 셸로 흘러가지 않는다.
 */
public final class Executables {

    private Executables() {
    }

    /** 실행 가능한 실제 경로를 찾으면 그것을, 못 찾으면 원래 이름을 그대로 돌려준다. */
    public static String resolve(String command) {
        if (command == null || command.isBlank()) {
            return command;
        }
        if (command.contains("/") || command.contains("\\")) {
            return command;
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return command;
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            for (String name : candidateNames(command)) {
                Path candidate = Path.of(dir, name);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
        }
        return command;
    }

    private static List<String> candidateNames(String command) {
        if (!isWindows()) {
            return List.of(command);
        }
        String lower = command.toLowerCase();
        if (lower.endsWith(".exe") || lower.endsWith(".cmd") || lower.endsWith(".bat") || lower.endsWith(".com")) {
            return List.of(command);
        }
        return List.of(command + ".exe", command + ".cmd", command + ".bat", command + ".com", command);
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
