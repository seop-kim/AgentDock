package com.agent.dock.agent.domain;

import com.agent.dock.execution.domain.Execution;
import com.agent.dock.permission.domain.PermissionProfile;
import com.agent.dock.project.domain.Project;
import com.agent.dock.provider.domain.AiProvider;
import com.agent.dock.role.domain.AgentRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "agent")
@Getter
@Setter
public class Agent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 에이전트는 프로젝트 안에서 만든다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "project_id", insertable = false, updatable = false)
    private Long projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private AgentRole role;

    @Column(name = "role_id", insertable = false, updatable = false)
    private Long roleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_profile_id", nullable = false)
    private PermissionProfile permissionProfile;

    @Column(name = "permission_profile_id", insertable = false, updatable = false)
    private Long permissionProfileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    @Column(name = "provider_id", insertable = false, updatable = false)
    private Long providerId;

    /** 이 에이전트만의 페르소나(성격/일하는 방식) 프롬프트. 런타임에 시스템 프롬프트로 덧붙인다. */
    @Column(columnDefinition = "text")
    private String persona;

    private String model;
    private String mode;

    /** 구성도에 놓인 상태인지. 마스터는 항상 놓여 있다(화면에서 뺄 수 없다). */
    @Column(nullable = false)
    private boolean placed = true;

    /** 손으로 옮긴 구성도 좌표(월드 좌표). null 이면 자동 배치를 따른다. */
    // Spring 의 snake_case 규칙은 마지막 대문자 앞에 밑줄을 넣지 않아 nodeX → nodex 가 되므로 이름을 고정한다.
    @Column(name = "node_x")
    private Double nodeX;

    @Column(name = "node_y")
    private Double nodeY;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> profile;

    @OneToMany(mappedBy = "agent")
    private List<Execution> executions;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
