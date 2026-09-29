package com.agent.dock.group;

import com.agent.dock.agent.Agent;
import com.agent.dock.project.Project;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_agent_id")
    private Agent leaderAgent;

    @Column(name = "leader_agent_id", insertable = false, updatable = false)
    private Long leaderAgentId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
