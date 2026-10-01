package com.agent.dock.delegation;

import com.agent.dock.execution.Execution;
import com.agent.dock.task.CommandRequest;
import com.agent.dock.task.CommandResponse;
import com.agent.dock.task.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 프로젝트 채팅 명령. 명령 하나가 Task 하나와 실행 트리 하나를 만든다.
 */
@RestController
@RequestMapping("/projects/{projectId}/commands")
@RequiredArgsConstructor
public class ProjectCommandController {

    private final TaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommandResponse send(@PathVariable Long projectId, @Valid @RequestBody CommandRequest request) {
        Execution root = taskService.command(projectId, request);
        return new CommandResponse(root.getTaskId(), root.getId());
    }
}
