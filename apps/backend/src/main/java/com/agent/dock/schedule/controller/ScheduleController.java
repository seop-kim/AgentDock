package com.agent.dock.schedule.controller;

import com.agent.dock.schedule.dto.CreateScheduleRequest;
import com.agent.dock.schedule.dto.ScheduleResponse;
import com.agent.dock.schedule.dto.ScheduleRunResponse;
import com.agent.dock.schedule.dto.UpdateScheduleRequest;
import com.agent.dock.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 반복·예약 실행 CRUD 와 즉시 실행. */
@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {
    private final ScheduleService service;

    @GetMapping
    public List<ScheduleResponse> findAll(@RequestParam(required = false) Long projectId) {
        return service.findAll(projectId);
    }

    @GetMapping("/{id}")
    public ScheduleResponse findOne(@PathVariable Long id) {
        return service.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(@Valid @RequestBody CreateScheduleRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ScheduleResponse update(@PathVariable Long id, @Valid @RequestBody UpdateScheduleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /** 즉시 실행 — 기존 채팅 명령 경로로 실행 트리를 시작한다. */
    @PostMapping("/{id}/run")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleRunResponse run(@PathVariable Long id) {
        return service.runNow(id);
    }
}
