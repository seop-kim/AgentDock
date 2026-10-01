package com.agent.dock.execution.domain;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.workspace.domain.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    // 실행 트리: 이 실행을 만든 부모 실행(루트는 null)과 트리의 루트 실행.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_execution_id")
    private Execution parentExecution;

    @Column(name = "parent_execution_id", insertable = false, updatable = false)
    private Long parentExecutionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_execution_id")
    private Execution rootExecution;

    @Column(name = "root_execution_id", insertable = false, updatable = false)
    private Long rootExecutionId;

    /**
     * 이 실행 트리가 도는 git worktree. 루트가 만들고 자식은 부모의 것을 물려받는다(같은 트리 = 같은 worktree).
     * 워크스페이스가 git 저장소가 아니거나 만들지 못하면 비어 있다(그때는 워크스페이스에서 실행).
     */
    @Column(name = "worktree_path", length = 1024)
    private String worktreePath;

    @Column(name = "worktree_branch", length = 200)
    private String worktreeBranch;

    /** 판단 실행이 마지막으로 낸 계약. 위임한 실행은 DELEGATE 로 남고(완료로 덮지 않는다) 위임 대상이 함께 남는다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExecutionDecision decision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegated_target_agent_id")
    private Agent delegatedTargetAgent;

    @Column(name = "delegated_target_agent_id", insertable = false, updatable = false)
    private Long delegatedTargetAgentId;

    /** 실행이 낸 최종 텍스트(계약 JSON 또는 작업 결과). */
    @Column(name = "result_text", columnDefinition = "text")
    private String resultText;

    /** 다음 실행으로 넘기는 최소 Handoff(요약·변경 파일 등). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> handoff;

    /** CLI `--output-format json` 이 돌려준 계측값. */
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer cacheReadTokens;
    private Integer cacheCreationTokens;

    @Column(precision = 12, scale = 6)
    private BigDecimal costUsd;

    private Long durationMs;
    private Integer numTurns;

    @Column(length = 64)
    private String sessionId;

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
