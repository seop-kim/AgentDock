package com.agent.dock.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByName(String name);

    @Query("select p from Project p join fetch p.workspace order by p.name asc")
    List<Project> findAllWithWorkspace();

    @Query("select p from Project p join fetch p.workspace where p.id = :id")
    Optional<Project> findWithWorkspace(@Param("id") Long id);
}
