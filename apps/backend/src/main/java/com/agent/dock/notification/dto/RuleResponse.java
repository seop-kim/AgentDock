package com.agent.dock.notification.dto;

import com.agent.dock.notification.domain.NotificationEventType;
import com.agent.dock.notification.domain.NotificationRule;

/** 알림 규칙 응답. */
public record RuleResponse(
        Long id,
        Long projectId,
        Long channelId,
        NotificationEventType eventType,
        boolean enabled
) {
    public static RuleResponse from(NotificationRule rule) {
        Long projectId = rule.getProjectId() != null ? rule.getProjectId()
                : (rule.getProject() == null ? null : rule.getProject().getId());
        Long channelId = rule.getChannelId() != null ? rule.getChannelId()
                : (rule.getChannel() == null ? null : rule.getChannel().getId());
        return new RuleResponse(rule.getId(), projectId, channelId, rule.getEventType(), rule.isEnabled());
    }
}