package com.agent.dock.notification.service;

import com.agent.dock.notification.domain.ChannelType;
import com.agent.dock.notification.domain.NotificationChannel;
import com.agent.dock.notification.interfaces.NotificationSender;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 채널 종류로 발송 구현을 골라 부른다(전략 패턴). 스프링이 등록된 {@code NotificationSender} 빈들을
 * List 로 주입하므로 새 종류는 빈만 추가하면 자동으로 색인된다(기존 코드를 고치지 않는다).
 */
@Component
public class NotificationDispatcher {

    private final Map<ChannelType, NotificationSender> senders;

    public NotificationDispatcher(List<NotificationSender> senders) {
        Map<ChannelType, NotificationSender> indexed = new EnumMap<>(ChannelType.class);
        for (NotificationSender sender : senders) {
            indexed.put(sender.type(), sender);
        }
        this.senders = Map.copyOf(indexed);
    }

    /** 채널 하나로 보낸다. 지원하지 않는 종류면 사유와 함께 실패를 돌려준다(예외 아님). */
    public NotificationSender.DeliveryResult dispatch(NotificationChannel channel, String subject, String body) {
        NotificationSender sender = senders.get(channel.getType());
        if (sender == null) {
            return NotificationSender.DeliveryResult.fail("지원하지 않는 채널입니다: " + channel.getType());
        }
        return sender.send(channel, subject, body);
    }
}