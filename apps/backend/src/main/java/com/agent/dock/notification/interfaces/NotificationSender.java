package com.agent.dock.notification.interfaces;

import com.agent.dock.notification.domain.ChannelType;
import com.agent.dock.notification.domain.NotificationChannel;

/**
 * 채널 종류별 발송 구현(포트). NotificationDispatcher 가 ChannelType 으로 구현을 골라 부른다
 * (스프링이 {@code List<NotificationSender>} 로 자동 색인한다 — 새 종류는 빈만 추가하면 된다).
 */
public interface NotificationSender {

    ChannelType type();

    /**
     * 실제 발송. 예외를 던지지 않고 DeliveryResult 로 성공/사유를 돌려준다
     * — 발송 실패가 요청 응답이나 예산 경고 스케줄러를 깨면 안 된다.
     */
    DeliveryResult send(NotificationChannel channel, String subject, String body);

    record DeliveryResult(boolean success, String detail) {
        public static DeliveryResult ok(String detail) {
            return new DeliveryResult(true, detail);
        }

        public static DeliveryResult fail(String detail) {
            return new DeliveryResult(false, detail);
        }
    }
}