package com.agent.dock.settings.controller;

import com.agent.dock.settings.dto.MergePolicyRequest;
import com.agent.dock.settings.dto.MergePolicyResponse;
import com.agent.dock.settings.service.MergePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 병합 정책 설정(GET/PUT /settings/merge-policy). 전역 설정이라 프로젝트 파라미터가 없다.
 * 본문이 없거나 값이 빠지면 400(@NotNull, GlobalExceptionHandler).
 */
@RestController
@RequestMapping("/settings/merge-policy")
@RequiredArgsConstructor
public class MergePolicyController {

    private final MergePolicyService service;

    @GetMapping
    public MergePolicyResponse get() {
        return service.get();
    }

    @PutMapping
    public MergePolicyResponse update(@Valid @RequestBody MergePolicyRequest request) {
        return service.update(request);
    }
}