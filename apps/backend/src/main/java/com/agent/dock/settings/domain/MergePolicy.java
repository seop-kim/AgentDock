package com.agent.dock.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 병합 정책(전역 싱글턴). 트리 결과가 MERGED 로 끝난 뒤 워크트리·브랜치를 자동 정리할지 정한다.
 *
 * <p>항상 한 행(id = 1)만 존재한다 — 자동 생성 id 가 없고 id 를 직접 1 로 고정한다. V27 의
 * {@code CHECK (id = 1)} 과 {@code SMALLINT} 타입이 엔티티의 {@link Short} id 와 1:1 로 맞는다.
 */
@Entity
@Table(name = "merge_policy")
@Getter
@Setter
public class MergePolicy {

    /** 싱글턴 행의 고정 id. */
    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    /** MERGED 로 끝난 트리의 워크트리를 자동 정리할지. */
    @Column(name = "auto_cleanup_enabled", nullable = false)
    private boolean autoCleanupEnabled;

    /** 자동 정리 시 전용 브랜치(agentdock/exec-*)도 함께 지울지. */
    @Column(name = "cleanup_delete_branch", nullable = false)
    private boolean cleanupDeleteBranch = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}