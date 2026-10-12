package com.agent.dock.notification.service;

import com.agent.dock.notification.domain.ChannelType;
import com.agent.dock.notification.domain.NotificationChannel;
import com.agent.dock.notification.interfaces.NotificationSender;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 일반 웹훅(WEBHOOK): config.url 로 {"subject","body"} JSON 을 POST 한다. */
@Component
@RequiredArgsConstructor
public class WebhookNotificationSender implements NotificationSender {

    private final HttpJsonClient http;
    private final ObjectMapper objectMapper;

    @Override
    public ChannelType type() {
        return ChannelType.WEBHOOK;
    }

    @Override
    public DeliveryResult send(NotificationChannel channel, String subject, String body) {
        String url = NotificationConfigs.string(channel, "url");
        if (url == null) {
            return DeliveryResult.fail("웹훅 URL(config.url)이 없습니다");
        }
        try {
            String json = objectMapper.writeValueAsString(Map.of("subject", subject, "body", body));
            HttpJsonClient.Result result = http.post(url, json);
            return result.success()
                    ? DeliveryResult.ok("HTTP " + result.statusCode())
                    : DeliveryResult.fail("HTTP " + result.statusCode() + " " + result.body());
        } catch (Exception ex) {
            return DeliveryResult.fail(String.valueOf(ex.getMessage()));
        }
    }
}