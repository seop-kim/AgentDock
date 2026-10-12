package com.agent.dock.notification.service;

import com.agent.dock.notification.domain.ChannelType;
import com.agent.dock.notification.domain.NotificationChannel;
import com.agent.dock.notification.interfaces.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 메일(EMAIL): config.to(쉼표 구분 가능)로 보낸다. spring.mail.host 가 없으면 JavaMailSender 빈이
 * 만들어지지 않으므로, 그때는 "메일 서버 미구성" 사유와 함께 실패로 돌려준다(테스트 발송 결과에 그대로 보인다).
 */
@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

    private final ObjectProvider<JavaMailSender> mailSender;

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    @Override
    public DeliveryResult send(NotificationChannel channel, String subject, String body) {
        String to = NotificationConfigs.string(channel, "to");
        if (to == null) {
            return DeliveryResult.fail("받는 사람(config.to)이 없습니다");
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            return DeliveryResult.fail("메일 서버가 구성되지 않았습니다(spring.mail.host 를 설정하세요)");
        }
        String from = NotificationConfigs.string(channel, "from");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from == null ? "agentdock@localhost" : from);
        message.setTo(to.split("\\s*,\\s*"));
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
            return DeliveryResult.ok("메일 발송됨: " + to);
        } catch (Exception ex) {
            return DeliveryResult.fail(String.valueOf(ex.getMessage()));
        }
    }
}