package com.agent.dock.notification.controller;

import com.agent.dock.notification.dto.ChannelResponse;
import com.agent.dock.notification.dto.CreateChannelRequest;
import com.agent.dock.notification.dto.CreateRuleRequest;
import com.agent.dock.notification.dto.RuleResponse;
import com.agent.dock.notification.dto.TestSendResponse;
import com.agent.dock.notification.dto.UpdateChannelRequest;
import com.agent.dock.notification.dto.UpdateRuleRequest;
import com.agent.dock.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 알림 채널·규칙 설정과 테스트 발송(웹훅·Slack·메일). */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;
    // ---- 채널 ----

    /** 채널 목록(projectId 가 있으면 그 프로젝트 + 전역). */
    @GetMapping("/channels")
    public List<ChannelResponse> channels(@RequestParam(required = false) Long projectId) {
        return service.listChannels(projectId);
    }

    @GetMapping("/channels/{id}")
    public ChannelResponse channel(@PathVariable Long id) {
        return service.getChannel(id);
    }

    @PostMapping("/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelResponse createChannel(@Valid @RequestBody CreateChannelRequest request) {
        return service.createChannel(request);
    }

    @PutMapping("/channels/{id}")
    public ChannelResponse updateChannel(@PathVariable Long id, @RequestBody UpdateChannelRequest request) {
        return service.updateChannel(id, request);
    }

    @DeleteMapping("/channels/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChannel(@PathVariable Long id) {
        service.deleteChannel(id);
    }

    /** 설정이 맞는지 확인하는 테스트 발송(성공/사유를 200 으로 돌려준다). */
    @PostMapping("/channels/{id}/test")
    @ResponseStatus(HttpStatus.CREATED)
    public TestSendResponse test(@PathVariable Long id) {
        return service.testSend(id);
    }
    // ---- 규칙 ----

    /** 규칙 목록(projectId 가 있으면 그 프로젝트 + 전역). */
    @GetMapping("/rules")
    public List<RuleResponse> rules(@RequestParam(required = false) Long projectId) {
        return service.listRules(projectId);
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse createRule(@Valid @RequestBody CreateRuleRequest request) {
        return service.createRule(request);
    }

    @PutMapping("/rules/{id}")
    public RuleResponse updateRule(@PathVariable Long id, @RequestBody UpdateRuleRequest request) {
        return service.updateRule(id, request);
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable Long id) {
        service.deleteRule(id);
    }
}