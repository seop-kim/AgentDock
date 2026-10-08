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
                                      @RequestParam(required = false) Long groupId,
                                      @RequestParam(required = false) Integer limit,
                                      @RequestParam(required = false) Long beforeId) {
        // limit 이 없으면 예전처럼 전부 준다(화면은 최근 것부터 몇 개씩 요청한다 — 트리까지 읽는 비용이 크다).
        if (limit == null) {
            return service.findAll(projectId, groupId);
        }
        return service.findPage(projectId, groupId, limit, beforeId);
    }

    @GetMapping("/{id}")
    public TaskResponse findOne(@PathVariable Long id) {
        return service.findOne(id);
    }
}
