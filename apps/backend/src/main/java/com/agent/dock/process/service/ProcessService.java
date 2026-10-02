package com.agent.dock.process.service;

import com.agent.dock.process.util.Executables;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 로컬 CLI(Claude Code, Codex 등) 프로세스 실행을 담당한다.
 * ProcessBuilder는 shell을 경유하지 않으므로 사용자 prompt가 셸 명령으로 해석되지 않는다.
 * 실행 파일 이름은 PATH/PATHEXT 로 해석한다(`npm` → `npm.cmd` 처럼 확장자 없는 이름도 실행되게).
 */
@Service
@Slf4j
public class ProcessService {
    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    public Process spawn(String executionId, String command, List<String> args, String cwd) {
        try {
            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(Executables.resolve(command));
            fullCommand.addAll(args);
            ProcessBuilder builder = new ProcessBuilder(fullCommand);
            // 없는 디렉터리에서 spawn 하면 원인을 알 수 없는 실패가 된다(예: 정리된 워크트리로 다시 병합).
            if (!new File(cwd).isDirectory()) {
                throw new IllegalStateException("작업 디렉터리가 없습니다: " + cwd);
            }
            builder.directory(new File(cwd));
            builder.environment().putAll(System.getenv());
            Process process = builder.start();
            processes.put(executionId, process);
            process.onExit().thenRun(() -> processes.remove(executionId));
            log.info("spawned execution={} command={}", executionId, command);
            return process;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to spawn process for execution " + executionId, ex);
        }
    }

    public boolean cancel(String executionId) {
        Process process = processes.get(executionId);
        if (process == null) {
            return false;
        }
        process.destroy();
        return true;
    }

    public boolean isRunning(String executionId) {
        return processes.containsKey(executionId);
    }
}
