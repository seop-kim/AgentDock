package com.agent.dock.project.repository;

import com.agent.dock.project.domain.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByName(String name);

    List<Project> findAllByOrderByNameAsc();
}
