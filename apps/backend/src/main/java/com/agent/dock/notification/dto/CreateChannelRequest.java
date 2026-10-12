package com.agent.dock.notification.dto;

import com.agent.dock.notification.domain.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** 알림 채널 생성(POST /notification-channels). projectId 가 null 이면 전역 채널. */
public record CreateChannelRequest(
        Long projectId,
        @NotNull ChannelType type,
        @NotBlank String name,
        Map<String, Object> config,
        Boolean enabled
) {
}