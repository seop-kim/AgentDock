package com.agent.dock.process;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로컬 CLI(Claude Code, Codex 등) 프로세스 실행을 담당한다.
 * ProcessBuilder는 shell을 경유하지 않으므로 사용자 prompt가 셸 명령으로 해석되지 않는다.
 */
@Service
@Slf4j
public class ProcessService {
    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    public Process spawn(String executionId, String command, List<String> args, String cwd) {
        try {
            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(command);
            fullCommand.addAll(args);
            ProcessBuilder builder = new ProcessBuilder(fullCommand);
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
