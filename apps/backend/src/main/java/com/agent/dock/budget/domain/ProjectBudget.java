package com.agent.dock.budget.domain;

import com.agent.dock.project.domain.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 프로젝트 비용 예산(프로젝트당 하나). 그 기간의 실행 비용 합계가 alertRatio(기본 0.8)를 넘으면 경고를
 * 한 번 보낸다 — alertedPeriod 에 그 기간 키를 남겨 같은 기간에 두 번 보내지 않는다.
 */
@Entity
@Table(name = "project_budget")
@Getter
@Setter
public class ProjectBudget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "project_id", insertable = false, updatable = false)
    private Long projectId;

    @Column(name = "amount_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountUsd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BudgetPeriod period = BudgetPeriod.MONTHLY;

    @Column(name = "alert_ratio", nullable = false, precision = 4, scale = 3)
    private BigDecimal alertRatio = new BigDecimal("0.80");

    /** 마지막으로 경고를 보낸 기간 키(예: monthly:2026-10-01T00:00:00Z). 같은 기간 재발송을 막는다. */
    @Column(name = "alerted_period", length = 40)
    private String alertedPeriod;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}