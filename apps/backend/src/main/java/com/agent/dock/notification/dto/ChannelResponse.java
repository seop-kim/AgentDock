package com.agent.dock.notification.dto;

import com.agent.dock.notification.domain.ChannelType;
import com.agent.dock.notification.domain.NotificationChannel;
import java.util.Map;

/** 알림 채널 응답. config(발송 설정)를 그대로 싣는다. */
public record ChannelResponse(
        Long id,
        Long projectId,
        ChannelType type,
        String name,
        Map<String, Object> config,
        boolean enabled
) {
    public static ChannelResponse from(NotificationChannel channel) {
        Long projectId = channel.getProjectId() != null ? channel.getProjectId()
                : (channel.getProject() == null ? null : channel.getProject().getId());
        return new ChannelResponse(channel.getId(), projectId, channel.getType(), channel.getName(),
                channel.getConfig(), channel.isEnabled());
    }
}