package com.agent.dock.notification.service;

import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.notification.domain.ChannelType;
import com.agent.dock.notification.domain.NotificationChannel;
import com.agent.dock.notification.domain.NotificationEventType;
import com.agent.dock.notification.domain.NotificationRule;
import com.agent.dock.notification.dto.ChannelResponse;
import com.agent.dock.notification.dto.CreateChannelRequest;
import com.agent.dock.notification.dto.CreateRuleRequest;
import com.agent.dock.notification.dto.RuleResponse;
import com.agent.dock.notification.dto.TestSendResponse;
import com.agent.dock.notification.dto.UpdateChannelRequest;
import com.agent.dock.notification.dto.UpdateRuleRequest;
import com.agent.dock.notification.interfaces.NotificationSender;
import com.agent.dock.notification.repository.NotificationChannelRepository;
import com.agent.dock.notification.repository.NotificationRuleRepository;
import com.agent.dock.project.domain.Project;
import com.agent.dock.project.repository.ProjectRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 알림 채널·규칙 CRUD 와 발송(테스트 발송·디스패치 라우팅)을 고정한다(H). */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationChannelRepository channelRepository;
    @Mock NotificationRuleRepository ruleRepository;
    @Mock ProjectRepository projectRepository;
    @Mock NotificationDispatcher dispatcher;
    @Mock EventPublisher changeEvents;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(channelRepository, ruleRepository, projectRepository, dispatcher,
                changeEvents);
    }
    private static NotificationChannel channel(Long id, ChannelType type, boolean enabled) {
        NotificationChannel channel = new NotificationChannel();
        channel.setId(id);
        channel.setType(type);
        channel.setName("ch" + id);
        channel.setEnabled(enabled);
        return channel;
    }

    private static Project project(Long id) {
        Project project = new Project();
        project.setId(id);
        return project;
    }

    @Test
    void createChannelStoresAndNotifies() {
        when(channelRepository.save(any(NotificationChannel.class))).thenAnswer(inv -> inv.getArgument(0));

        ChannelResponse response = service.createChannel(new CreateChannelRequest(
                null, ChannelType.WEBHOOK, "hooks", Map.of("url", "https://example.com/hook"), null));

        assertThat(response.name()).isEqualTo("hooks");
        assertThat(response.type()).isEqualTo(ChannelType.WEBHOOK);
        assertThat(response.enabled()).isTrue();
        verify(changeEvents).notificationChanged(null);
    }

    @Test
    void createChannelRejectsMissingConfigPerKind() {
        assertThatThrownBy(() -> service.createChannel(new CreateChannelRequest(
                null, ChannelType.WEBHOOK, "w", Map.of(), null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.createChannel(new CreateChannelRequest(
                null, ChannelType.SLACK, "s", Map.of(), null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.createChannel(new CreateChannelRequest(
                null, ChannelType.EMAIL, "e", Map.of(), null)))
                .isInstanceOf(BadRequestException.class);
        verify(channelRepository, never()).save(any());
    }
    @Test
    void createChannelForUnknownProjectIsNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createChannel(new CreateChannelRequest(
                99L, ChannelType.WEBHOOK, "w", Map.of("url", "https://x"), null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateChannelAppliesChangesAndNotifies() {
        NotificationChannel channel = channel(7L, ChannelType.WEBHOOK, true);
        channel.setConfig(Map.of("url", "https://old"));
        when(channelRepository.findById(7L)).thenReturn(Optional.of(channel));
        when(channelRepository.save(any(NotificationChannel.class))).thenAnswer(inv -> inv.getArgument(0));

        ChannelResponse response = service.updateChannel(7L,
                new UpdateChannelRequest("renamed", Map.of("url", "https://new"), false));

        assertThat(response.name()).isEqualTo("renamed");
        assertThat(response.enabled()).isFalse();
        verify(changeEvents).notificationChanged(null);
    }

    @Test
    void deleteChannelRemovesAndNotifies() {
        NotificationChannel channel = channel(7L, ChannelType.SLACK, true);
        when(channelRepository.findById(7L)).thenReturn(Optional.of(channel));

        service.deleteChannel(7L);

        verify(channelRepository).delete(channel);
        verify(changeEvents).notificationChanged(null);
    }

    @Test
    void getChannelMissingIsNotFound() {
        when(channelRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getChannel(5L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listChannelsWithProjectIncludesGlobal() {
        when(projectRepository.findById(5L)).thenReturn(Optional.of(project(5L)));
        when(channelRepository.findByProjectIdIsNullOrProjectIdOrderByIdDesc(5L))
                .thenReturn(List.of(channel(2L, ChannelType.SLACK, true), channel(1L, ChannelType.WEBHOOK, true)));

        List<ChannelResponse> channels = service.listChannels(5L);

        assertThat(channels).hasSize(2);
        assertThat(channels.get(0).type()).isEqualTo(ChannelType.SLACK);
    }
    @Test
    void createRuleStoresAndNotifies() {
        when(channelRepository.findById(3L)).thenReturn(Optional.of(channel(3L, ChannelType.WEBHOOK, true)));
        when(ruleRepository.save(any(NotificationRule.class))).thenAnswer(inv -> inv.getArgument(0));

        RuleResponse response = service.createRule(new CreateRuleRequest(
                null, 3L, NotificationEventType.WAITING_INPUT, null));

        assertThat(response.channelId()).isEqualTo(3L);
        assertThat(response.eventType()).isEqualTo(NotificationEventType.WAITING_INPUT);
        assertThat(response.enabled()).isTrue();
        verify(changeEvents).notificationChanged(null);
    }

    @Test
    void createRuleWithMissingChannelIsNotFound() {
        when(channelRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRule(new CreateRuleRequest(
                null, 3L, NotificationEventType.EXECUTION_FAILED, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateRuleAppliesChanges() {
        NotificationRule rule = new NotificationRule();
        rule.setId(9L);
        rule.setChannel(channel(3L, ChannelType.WEBHOOK, true));
        rule.setEventType(NotificationEventType.WAITING_INPUT);
        when(ruleRepository.findById(9L)).thenReturn(Optional.of(rule));
        when(channelRepository.findById(4L)).thenReturn(Optional.of(channel(4L, ChannelType.EMAIL, true)));
        when(ruleRepository.save(any(NotificationRule.class))).thenAnswer(inv -> inv.getArgument(0));

        RuleResponse response = service.updateRule(9L,
                new UpdateRuleRequest(4L, NotificationEventType.EXECUTION_FAILED, false));

        assertThat(response.channelId()).isEqualTo(4L);
        assertThat(response.eventType()).isEqualTo(NotificationEventType.EXECUTION_FAILED);
        assertThat(response.enabled()).isFalse();
    }

    @Test
    void deleteRuleRemovesAndNotifies() {
        NotificationRule rule = new NotificationRule();
        rule.setId(9L);
        rule.setChannel(channel(3L, ChannelType.WEBHOOK, true));
        when(ruleRepository.findById(9L)).thenReturn(Optional.of(rule));

        service.deleteRule(9L);

        verify(ruleRepository).delete(rule);
        verify(changeEvents).notificationChanged(null);
    }
    @Test
    void testSendReturnsSenderResult() {
        NotificationChannel channel = channel(7L, ChannelType.WEBHOOK, true);
        when(channelRepository.findById(7L)).thenReturn(Optional.of(channel));
        when(dispatcher.dispatch(any(NotificationChannel.class), any(), any()))
                .thenReturn(NotificationSender.DeliveryResult.ok("HTTP 200"));

        TestSendResponse response = service.testSend(7L);

        assertThat(response.channelId()).isEqualTo(7L);
        assertThat(response.success()).isTrue();
        assertThat(response.detail()).isEqualTo("HTTP 200");
    }

    @Test
    void notifyRoutesToEnabledChannelsOnly() {
        NotificationRule enabled = new NotificationRule();
        enabled.setChannel(channel(1L, ChannelType.WEBHOOK, true));
        NotificationRule disabled = new NotificationRule();
        disabled.setChannel(channel(2L, ChannelType.WEBHOOK, false));
        when(ruleRepository.findActive(NotificationEventType.EXECUTION_FINISHED, 2L))
                .thenReturn(List.of(enabled, disabled));
        when(dispatcher.dispatch(any(NotificationChannel.class), any(), any()))
                .thenReturn(NotificationSender.DeliveryResult.ok("HTTP 200"));

        int sent = service.notify(NotificationEventType.EXECUTION_FINISHED, 2L, "s", "b");

        assertThat(sent).isEqualTo(1);
        verify(dispatcher, times(1)).dispatch(any(NotificationChannel.class), any(), any());
    }
}