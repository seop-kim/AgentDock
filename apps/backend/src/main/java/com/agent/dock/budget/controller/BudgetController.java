package com.agent.dock.budget.controller;

import com.agent.dock.budget.dto.ProjectBudgetResponse;
import com.agent.dock.budget.dto.UpdateBudgetRequest;
import com.agent.dock.budget.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 프로젝트 예산(기간별 사용 현황·80% 경고). 기간별 사용량 집계는 GET /executions/usage 다. */
@RestController
@RequestMapping("/projects/{projectId}/budget")
@RequiredArgsConstructor
public class BudgetController {
    private final BudgetService service;

    /** 예산과 이번 기간 사용 현황(예산이 없으면 configured=false). 없는 프로젝트면 404. */
    @GetMapping
    public ProjectBudgetResponse get(@PathVariable Long projectId) {
        return service.budget(projectId);
    }

    /** 예산 설정(없으면 만든다, 있으면 고친다). amountUsd 필수. */
    @PutMapping
    public ProjectBudgetResponse update(@PathVariable Long projectId,
                                        @Valid @RequestBody UpdateBudgetRequest request) {
                return service.update(projectId, request);
    }

    /** 예산 삭제(없어도 204 - 멱등). 프로젝트가 없으면 404. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long projectId) {
        service.delete(projectId);
    }
}