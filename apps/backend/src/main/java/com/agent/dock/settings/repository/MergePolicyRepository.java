package com.agent.dock.settings.repository;

import com.agent.dock.settings.domain.MergePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

/** 병합 정책(싱글턴 — 항상 id = 1 한 행). */
public interface MergePolicyRepository extends JpaRepository<MergePolicy, Short> {
}