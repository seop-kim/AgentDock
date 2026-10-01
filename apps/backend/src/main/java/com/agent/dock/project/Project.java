package com.agent.dock.project;

import com.agent.dock.agent.Agent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "project")
@Getter
@Setter
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    /** 프로젝트 마스터 에이전트(최상위 리더). 그룹에 속하지 않고, 프롬프트 계층의 맨 위에 선다. */
    // 응답에 마스터 이름을 함께 보여줘야 해서 즉시 로딩한다(프로젝트 한 행당 1건).
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "master_agent_id")
    private Agent masterAgent;

    @Column(name = "master_agent_id", insertable = false, updatable = false)
    private Long masterAgentId;

    /** 마스터 프롬프트. 그룹·에이전트 프롬프트보다 먼저 적용된다. */
    @Column(name = "master_prompt", nullable = false, columnDefinition = "text")
    private String masterPrompt = "";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
