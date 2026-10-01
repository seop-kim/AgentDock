package com.agent.dock.attachment.domain;

import com.agent.dock.workspace.domain.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 명령에 붙인 파일.
 * 실제 파일은 워크스페이스 폴더 안 `.agentdock/attachments` 로 복사되고,
 * 에이전트는 저장 경로(storedPath, 워크스페이스 기준 상대 경로)로 읽는다.
 */
@Entity
@Table(name = "attachment")
@Getter
@Setter
public class Attachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "workspace_id", insertable = false, updatable = false)
    private Long workspaceId;

    /** 이 첨부가 붙은 명령(태스크). 아직 붙지 않았으면 null. */
    @Column(name = "task_id")
    private Long taskId;

    /** 사용자가 올린 원래 이름(화면에 보여 준다). */
    @Column(nullable = false)
    private String originalName;

    /** 워크스페이스 기준 상대 경로(실제 파일 위치). */
    @Column(nullable = false)
    private String storedPath;

    private Long sizeBytes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
