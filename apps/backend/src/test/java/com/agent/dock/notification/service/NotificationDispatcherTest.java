package com.agent.dock.notification.service;

import com.agent.dock.notification.domain.ChannelType;
import com.agent.dock.notification.domain.NotificationChannel;
import com.agent.dock.notification.interfaces.NotificationSender;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 채널 종류로 발송 구현을 고르는 디스패처(H). */
class NotificationDispatcherTest {

    private static NotificationSender sender(ChannelType type, NotificationSender.DeliveryResult result) {
        return new NotificationSender() {
            public ChannelType type() {
                return type;
            }

            public DeliveryResult send(NotificationChannel channel, String subject, String body) {
                return result;
            }
        };
    }

    private static NotificationChannel channel(ChannelType type) {
        NotificationChannel channel = new NotificationChannel();
        channel.setType(type);
        return channel;
    }

    @Test
    void dispatchRoutesToSenderForChannelType() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(
                List.of(sender(ChannelType.WEBHOOK, NotificationSender.DeliveryResult.ok("ok")),
                        sender(ChannelType.SLACK, NotificationSender.DeliveryResult.fail("nope"))));

        assertThat(dispatcher.dispatch(channel(ChannelType.WEBHOOK), "s", "b").success()).isTrue();
        assertThat(dispatcher.dispatch(channel(ChannelType.SLACK), "s", "b").detail()).isEqualTo("nope");
    }

    @Test
    void dispatchWithoutSenderForTypeFails() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(
                List.of(sender(ChannelType.WEBHOOK, NotificationSender.DeliveryResult.ok("ok"))));

        NotificationSender.DeliveryResult result = dispatcher.dispatch(channel(ChannelType.EMAIL), "s", "b");

        assertThat(result.success()).isFalse();
        assertThat(result.detail()).contains("지원하지 않는");
    }
}