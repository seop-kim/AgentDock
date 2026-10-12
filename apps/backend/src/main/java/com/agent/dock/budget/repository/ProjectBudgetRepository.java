package com.agent.dock.budget.repository;

import com.agent.dock.budget.domain.ProjectBudget;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectBudgetRepository extends JpaRepository<ProjectBudget, Long> {
    Optional<ProjectBudget> findByProjectId(Long projectId);
}