package com.agent.dock.agent;

import com.agent.dock.execution.Execution;
import com.agent.dock.permission.PermissionProfile;
import com.agent.dock.provider.AiConnection;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.role.AgentRole;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    private AiConnection connection;

    @Column(name = "connection_id", insertable = false, updatable = false)
    private Long connectionId;

    private String model;
    private String mode;

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
