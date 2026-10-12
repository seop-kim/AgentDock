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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * 알림 채널·규칙 CRUD 와 발송(테스트 발송 포함)을 맡는다.
 *
 * <p>채널은 웹훅·Slack·메일 세 종류(POST/GET/PUT/DELETE /notification-channels)이고, 규칙은 "어떤 사건
 * (EXECUTION_FINISHED 등)을 어느 채널로 보낼지"를 잇는다(POST/GET/PUT/DELETE /notification-rules).
 * projectId 가 null 인 채널·규칙은 전역이다. 발송은 종류별 구현(NotificationSender)에 위임하고,
 * 실패는 예외가 아니라 결과로 돌려준다(테스트 발송 응답에 그대로 보인다).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationChannelRepository channelRepository;
    private final NotificationRuleRepository ruleRepository;
    private final ProjectRepository projectRepository;
    private final NotificationDispatcher dispatcher;
    /** 전역 SSE 스트림에 "알림 설정이 바뀌었다"를 알린다. */
    private final EventPublisher changeEvents;
    // ---- 채널 ----

    /** 채널 목록(projectId 가 있으면 그 프로젝트 + 전역, 없으면 전부). */
    public List<ChannelResponse> listChannels(Long projectId) {
        if (projectId != null) {
            requireProject(projectId);
            return channelRepository.findByProjectIdIsNullOrProjectIdOrderByIdDesc(projectId).stream()
                    .map(ChannelResponse::from).toList();
        }
        return channelRepository.findAllByOrderByIdDesc().stream().map(ChannelResponse::from).toList();
    }

    @Transactional
    public ChannelResponse createChannel(CreateChannelRequest request) {
        Project project = request.projectId() == null ? null : requireProject(request.projectId());
        Map<String, Object> config = request.config() == null ? new HashMap<>() : new HashMap<>(request.config());
        requireConfig(request.type(), config);
        NotificationChannel channel = new NotificationChannel();
        channel.setProject(project);
        channel.setType(request.type());
        channel.setName(request.name().trim());
        channel.setConfig(config);
        if (request.enabled() != null) {
            channel.setEnabled(request.enabled());
        }
        ChannelResponse response = ChannelResponse.from(channelRepository.save(channel));
        changeEvents.notificationChanged(request.projectId());
        return response;
    }
    public ChannelResponse getChannel(Long id) {
        return ChannelResponse.from(requireChannel(id));
    }

    @Transactional
    public ChannelResponse updateChannel(Long id, UpdateChannelRequest request) {
        NotificationChannel channel = requireChannel(id);
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("채널 이름은 비울 수 없습니다");
            }
            channel.setName(request.name().trim());
        }
        if (request.config() != null) {
            Map<String, Object> config = new HashMap<>(request.config());
            requireConfig(channel.getType(), config);
            channel.setConfig(config);
        }
        if (request.enabled() != null) {
            channel.setEnabled(request.enabled());
        }
        ChannelResponse response = ChannelResponse.from(channelRepository.save(channel));
        changeEvents.notificationChanged(channel.getProjectId());
        return response;
    }

    /** 채널 삭제. 그 채널을 쓰는 규칙도 함께 지워진다(channel_id ON DELETE CASCADE). */
    @Transactional
    public void deleteChannel(Long id) {
        NotificationChannel channel = requireChannel(id);
        channelRepository.delete(channel);
        changeEvents.notificationChanged(channel.getProjectId());
    }
    // ---- 규칙 ----

    /** 규칙 목록(projectId 가 있으면 그 프로젝트 + 전역, 없으면 전부). */
    public List<RuleResponse> listRules(Long projectId) {
        if (projectId != null) {
            requireProject(projectId);
            return ruleRepository.findByProjectIdIsNullOrProjectIdOrderByIdDesc(projectId).stream()
                    .map(RuleResponse::from).toList();
        }
        return ruleRepository.findAllByOrderByIdDesc().stream().map(RuleResponse::from).toList();
    }

    @Transactional
    public RuleResponse createRule(CreateRuleRequest request) {
        Project project = request.projectId() == null ? null : requireProject(request.projectId());
        NotificationChannel channel = requireChannel(request.channelId());
        NotificationRule rule = new NotificationRule();
        rule.setProject(project);
        rule.setChannel(channel);
        rule.setEventType(request.eventType());
        if (request.enabled() != null) {
            rule.setEnabled(request.enabled());
        }
        RuleResponse response = RuleResponse.from(ruleRepository.save(rule));
        changeEvents.notificationChanged(request.projectId());
        return response;
    }
    @Transactional
    public RuleResponse updateRule(Long id, UpdateRuleRequest request) {
        NotificationRule rule = requireRule(id);
        if (request.channelId() != null) {
            rule.setChannel(requireChannel(request.channelId()));
        }
        if (request.eventType() != null) {
            rule.setEventType(request.eventType());
        }
        if (request.enabled() != null) {
            rule.setEnabled(request.enabled());
        }
        RuleResponse response = RuleResponse.from(ruleRepository.save(rule));
        changeEvents.notificationChanged(rule.getProjectId());
        return response;
    }

    @Transactional
    public void deleteRule(Long id) {
        NotificationRule rule = requireRule(id);
        ruleRepository.delete(rule);
        changeEvents.notificationChanged(rule.getProjectId());
    }
    // ---- 발송 ----

    /** 채널 하나로 테스트 메시지를 보낸다(설정이 맞는지 화면에서 확인). 발송 실패도 200 으로 결과를 돌려준다. */
    public TestSendResponse testSend(Long channelId) {
        NotificationChannel channel = requireChannel(channelId);
        NotificationSender.DeliveryResult result = dispatcher.dispatch(channel,
                "[AgentDock] 알림 테스트", "이 채널로 알림이 정상 발송되었습니다.\n채널: " + channel.getName());
        return new TestSendResponse(channelId, result.success(), result.detail());
    }

    /** 그 사건을 (전역 or 그 프로젝트) 활성 규칙들의 채널로 보낸다. 실제로 보낸 건수를 돌려준다. */
    public int notify(NotificationEventType eventType, Long projectId, String subject, String body) {
        List<NotificationRule> rules = ruleRepository.findActive(eventType, projectId);
        int sent = 0;
        for (NotificationRule rule : rules) {
            NotificationChannel channel = rule.getChannel();
            if (channel == null || !channel.isEnabled()) {
                continue;
            }
            NotificationSender.DeliveryResult result = dispatcher.dispatch(channel, subject, body);
            if (result.success()) {
                sent++;
            } else {
                log.warn("[notification] 발송 실패 event={} channel={}: {}",
                        eventType, channel.getId(), result.detail());
            }
        }
        return sent;
    }
    // ---- 도우미 ----

    /** 채널 종류별로 config 필수 키가 있는지 확인한다(없으면 400). */
    private static void requireConfig(ChannelType type, Map<String, Object> config) {
        switch (type) {
            case WEBHOOK -> requireKey(config, "url", "웹훅 URL(config.url)이 필요합니다");
            case SLACK -> {
                if (!hasText(config, "webhookUrl") && !hasText(config, "url")) {
                    throw new BadRequestException("Slack 웹훅 URL(config.webhookUrl)이 필요합니다");
                }
            }
            case EMAIL -> requireKey(config, "to", "받는 사람(config.to)이 필요합니다");
        }
    }

    private static void requireKey(Map<String, Object> config, String key, String message) {
        if (!hasText(config, key)) {
            throw new BadRequestException(message);
        }
    }

    private static boolean hasText(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value != null && !String.valueOf(value).isBlank();
    }

    private NotificationChannel requireChannel(Long id) {
        return channelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("NotificationChannel %d not found".formatted(id)));
    }

    private NotificationRule requireRule(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("NotificationRule %d not found".formatted(id)));
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(projectId)));
    }
}