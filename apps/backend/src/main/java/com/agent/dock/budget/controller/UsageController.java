package com.agent.dock.budget.controller;

import com.agent.dock.budget.dto.UsageResponse;
import com.agent.dock.budget.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기간별 사용량 집계(비용·토큰). projectId 를 주면 그 프로젝트만, 없으면 전역. */
@RestController
@RequestMapping("/executions/usage")
@RequiredArgsConstructor
public class UsageController {
    private final BudgetService service;

    /**
     * from/to 는 ISO-8601 Instant(예: 2026-10-01T00:00:00Z), bucket 은 DAY/WEEK/MONTH(기본 DAY).
     * 생략하면 최근 30일을 하루 단위로 본다.
     */
    @GetMapping
    public UsageResponse usage(@RequestParam(required = false) Long projectId,
                               @RequestParam(required = false) String from,
                               @RequestParam(required = false) String to,
                               @RequestParam(required = false) String bucket) {
        return service.usage(projectId, from, to, bucket);
    }
}