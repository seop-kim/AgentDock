package com.agent.dock.permission.domain;

import com.agent.dock.agent.domain.Agent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "permission_profile")
@Getter
@Setter
public class PermissionProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean fileRead = false;
    @Column(nullable = false)
    private boolean fileWrite = false;
    @Column(nullable = false)
    private boolean terminalExecute = false;
    @Column(nullable = false)
    private boolean gitStatus = true;
    @Column(nullable = false)
    private boolean gitDiff = false;
    @Column(nullable = false)
    private boolean gitCommit = false;
    @Column(nullable = false)
    private boolean gitPush = false;
    @Column(nullable = false)
    private boolean dbRead = false;
    @Column(nullable = false)
    private boolean dbWrite = false;
    @Column(nullable = false)
    private boolean dbSchemaChange = false;
    @Column(nullable = false)
    private boolean deploy = false;
    @Column(nullable = false)
    private boolean externalNetworkAccess = false;

    @OneToMany(mappedBy = "permissionProfile")
    private List<Agent> agents;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
