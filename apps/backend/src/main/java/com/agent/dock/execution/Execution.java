package com.agent.dock.execution;

import com.agent.dock.agent.Agent;
import com.agent.dock.workspace.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "execution")
@Getter
@Setter
public class Execution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "agent_id", insertable = false, updatable = false)
    private Long agentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "workspace_id", insertable = false, updatable = false)
    private Long workspaceId;

    // Task 실행으로 만들어진 실행이면 해당 Task id. 관계 매핑 없이 컬럼만 두어 task 패키지 의존을 피한다.
    @Column(name = "task_id")
    private Long taskId;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status = ExecutionStatus.PENDING;

    private Instant startedAt;
    private Instant finishedAt;
    private Integer exitCode;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @OneToMany(mappedBy = "execution")
    private List<ExecutionLog> logs;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
