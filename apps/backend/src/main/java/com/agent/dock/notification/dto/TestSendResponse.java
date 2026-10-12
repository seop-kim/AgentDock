package com.agent.dock.notification.dto;

/** 테스트 발송 결과(POST /notification-channels/{id}/test). */
public record TestSendResponse(
        Long channelId,
        boolean success,
        String detail
) {
}