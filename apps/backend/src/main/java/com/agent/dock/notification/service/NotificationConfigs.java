package com.agent.dock.notification.service;

import com.agent.dock.notification.domain.NotificationChannel;
import java.util.Map;

/** 채널 config(JSONB)에서 값을 꺼내는 작은 도우미(종류별 발송 구현들이 함께 쓴다). */
final class NotificationConfigs {

    private NotificationConfigs() {
    }

    /** 문자열 값(trim, 빈 값은 null). 없으면 null. */
    static String string(NotificationChannel channel, String key) {
        Map<String, Object> config = channel.getConfig();
        if (config == null) {
            return null;
        }
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}