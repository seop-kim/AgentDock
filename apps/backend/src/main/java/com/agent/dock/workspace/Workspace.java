package com.agent.dock.workspace;

import com.agent.dock.agent.Agent;
import com.agent.dock.execution.Execution;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "workspace")
@Getter
@Setter
public class Workspace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true, length = 1024)
    private String path;

    private String description;

    @OneToMany(mappedBy = "workspace")
    private List<Agent> agents;

    @OneToMany(mappedBy = "workspace")
    private List<Execution> executions;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
