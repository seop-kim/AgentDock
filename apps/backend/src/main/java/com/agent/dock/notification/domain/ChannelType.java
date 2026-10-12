package com.agent.dock.notification.domain;

/** 알림 채널 종류. config 의 키가 종류마다 다르다(웹훅·Slack: URL, 메일: to·from). */
public enum ChannelType {
    WEBHOOK,
    SLACK,
    EMAIL
}