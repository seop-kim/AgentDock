package com.agent.dock.task.controller;

import com.agent.dock.execution.dto.ExecutionResponse;
import com.agent.dock.task.domain.Task;
import com.agent.dock.task.dto.CreateTaskRequest;
import com.agent.dock.task.dto.TaskResponse;
import com.agent.dock.task.service.TaskService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService service;

    @GetMapping
    public List<TaskResponse> findAll(@RequestParam(required = false) Long projectId,
                                      @RequestParam(required = false) Long groupId) {
        return service.findAll(projectId, groupId);
    }

    @GetMapping("/{id}")
    public TaskResponse findOne(@PathVariable Long id) {
        return service.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/run")
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionResponse run(@PathVariable Long id) {
        return service.run(id);
    }
}
