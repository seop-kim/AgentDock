package com.agent.dock.group.domain;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.project.domain.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 프로젝트를 수행하는 팀. 'group' 은 SQL 예약어라 테이블명은 agent_group 을 쓴다.
 */
@Entity
@Table(name = "agent_group")
@Getter
@Setter
public class AgentGroup {
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

    private String description;

    /** 그룹 프롬프트. 마스터 프롬프트 아래, 에이전트 프롬프트 위에 적용된다. */
    @Column(nullable = false, columnDefinition = "text")
    private String prompt = "";

    /**
     * 그룹 **공유 노트**. 그룹 프롬프트가 "규칙"이라면 이쪽은 **맥락**이다 — 그룹 안에서 일한 실행들이
     * 알아낸 사실을 적어 두면, 같은 그룹의 실행들이 프롬프트에 함께 실어 보고 일한다(런타임이 달라도 공유된다).
     */
    @Column(name = "shared_note", nullable = false, columnDefinition = "text")
    private String sharedNote = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_agent_id")
    private Agent leaderAgent;

    @Column(name = "leader_agent_id", insertable = false, updatable = false)
    private Long leaderAgentId;

    /** 손으로 옮긴 그룹 상자 좌표(월드 좌표). null 이면 자동 배치를 따른다. */
    // Spring 의 snake_case 규칙은 마지막 대문자 앞에 밑줄을 넣지 않아 nodeX → nodex 가 되므로 이름을 고정한다.
    @Column(name = "node_x")
    private Double nodeX;

    @Column(name = "node_y")
    private Double nodeY;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
