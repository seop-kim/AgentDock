package com.agent.dock.task.controller;

import com.agent.dock.task.dto.TaskResponse;
import com.agent.dock.task.service.TaskService;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
}
