package com.agent.dock.notification.dto;

import com.agent.dock.notification.domain.NotificationEventType;

/** 알림 규칙 수정(PUT /notification-rules/{id}). null 인 필드는 바꾸지 않는다. */
public record UpdateRuleRequest(
        Long channelId,
        NotificationEventType eventType,
        Boolean enabled
) {
}