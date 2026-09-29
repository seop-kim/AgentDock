package com.agent.dock.workspace;

import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.ConnectionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 워크스페이스(폴더)에서 특정 런타임이 실제로 실행/응답되는지 확인한 결과.
 * 로그인은 런타임 전역이고, 상태는 폴더마다 다를 수 있어 이 테이블이 폴더별로 관리한다.
 */
@Entity
@Table(name = "workspace_runtime_status")
@Getter
@Setter
public class WorkspaceRuntimeStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus status = ConnectionStatus.DISCONNECTED;

    private Instant lastCheckedAt;

    @Column(columnDefinition = "text")
    private String lastError;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
