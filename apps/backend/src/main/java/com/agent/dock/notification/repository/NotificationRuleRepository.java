package com.agent.dock.notification.repository;

import com.agent.dock.notification.domain.NotificationEventType;
import com.agent.dock.notification.domain.NotificationRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {

    List<NotificationRule> findAllByOrderByIdDesc();

    /** 그 프로젝트 규칙 + 전역 규칙(project_id null). */
    List<NotificationRule> findByProjectIdIsNullOrProjectIdOrderByIdDesc(Long projectId);

    /** 그 사건의 활성 규칙 중 (전역 or 그 프로젝트) 규칙. 채널을 함께 읽어 지연 로딩을 피한다. */
    @Query("select r from NotificationRule r join fetch r.channel "
            + "where r.enabled = true and r.eventType = :eventType "
            + "and (r.projectId is null or r.projectId = :projectId)")
    List<NotificationRule> findActive(@Param("eventType") NotificationEventType eventType,
                                      @Param("projectId") Long projectId);
}