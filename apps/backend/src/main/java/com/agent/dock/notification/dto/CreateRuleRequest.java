package com.agent.dock.notification.dto;

import com.agent.dock.notification.domain.NotificationEventType;
import jakarta.validation.constraints.NotNull;

/** 알림 규칙 생성(POST /notification-rules). projectId 가 null 이면 전역 규칙. */
public record CreateRuleRequest(
        Long projectId,
        @NotNull Long channelId,
        @NotNull NotificationEventType eventType,
        Boolean enabled
) {
}