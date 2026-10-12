package com.agent.dock.schedule.domain;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.group.domain.AgentGroup;
import com.agent.dock.project.domain.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 반복·예약 실행. 명령(프롬프트)을 cron 주기에 따라 자동으로 보낸다.
 * 실행은 채팅 명령 경로(POST /projects/{id}/commands -> TaskService.command)를 그대로 재사용한다.
 */
@Entity
@Table(name = "schedule")
@Getter
@Setter
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "project_id", insertable = false, updatable = false)
    private Long projectId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    /** 대상 에이전트. 없으면 프로젝트 마스터가 받는다(groupId 와 함께 쓸 수 없다). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_agent_id")
    private Agent targetAgent;

    @Column(name = "target_agent_id", insertable = false, updatable = false)
    private Long targetAgentId;

    /** 대상 그룹(팀). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private AgentGroup targetGroup;

    @Column(name = "group_id", insertable = false, updatable = false)
    private Long groupId;

    /** Spring CronExpression 6필드(초 분 시 일 월 요일). 예) 0 0 9 * * * = 매일 09:00. */
    @Column(nullable = false, length = 120)
    private String cron;

    @Column(nullable = false)
    private boolean enabled = true;

    /** 놓친 실행 처리: SKIP / RUN_ONCE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "missed_policy", nullable = false, length = 20)
    private MissedPolicy missedPolicy = MissedPolicy.SKIP;

    /** 다음 실행 시각. 스케줄러가 이 값이 지난 것만 훑는다. */
    @Column(name = "next_run_at")
    private Instant nextRunAt;

    /** 마지막 실행 시각. */
    @Column(name = "last_run_at")
    private Instant lastRunAt;

    /** 마지막 실행이 만든 Task id. */
    @Column(name = "last_task_id")
    private Long lastTaskId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
