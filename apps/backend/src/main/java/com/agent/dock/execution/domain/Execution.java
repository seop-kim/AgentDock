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

    /**
     * 트리(루트 실행)가 끝날 때 그 워크트리를 트리 브랜치에 커밋한 짧은 sha. 커밋할 것이 없었으면 비어 있다.
     * 트리 결과는 루트의 것이므로 **루트 실행에만** 채운다(자식은 비어 있다).
     */
    @Column(name = "result_commit", length = 64)
    private String resultCommit;

    /** 그 커밋을 메인 저장소로 되돌린 결과(MERGED/MANUAL/NONE). 되돌리지 않았으면 비어 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "merge_status", length = 20)
    private MergeStatus mergeStatus;

    /** 자동 병합하지 못한 사유(MANUAL 이면 채워진다). 수동 병합 대상 브랜치는 로그에 남는다. */
    @Column(name = "merge_detail", columnDefinition = "text")
    private String mergeDetail;

    /** 트리가 바꾼 파일 목록(git `diff --name-status <base>...HEAD`): `{status, path}` 배열. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_files", columnDefinition = "jsonb")
    private List<ChangedFile> changedFiles;

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

    /**
     * 판단 실행이 사람에게 물은 질문(계약 `{"action":"ask"}`). 이 값이 채워져 있고 상태가 `WAITING_INPUT` 이면
     * 실행은 끝나지 않고 사람의 답을 기다린다(답이 오면 같은 실행의 판단 루프를 이어서 돈다).
     */
    @Column(columnDefinition = "text")
    private String question;

    /** 그 질문에 대한 사람의 답(`POST /executions/{id}/answer`). 다음 판단 스텝의 프롬프트에 붙는다. */
    @Column(columnDefinition = "text")
    private String answer;

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
