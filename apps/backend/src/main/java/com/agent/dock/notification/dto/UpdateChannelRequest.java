package com.agent.dock.notification.dto;

import java.util.Map;

/** 알림 채널 수정(PUT /notification-channels/{id}). null 인 필드는 바꾸지 않는다. */
public record UpdateChannelRequest(
        String name,
        Map<String, Object> config,
        Boolean enabled
) {
}