package com.agent.dock.provider;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {
    @EntityGraph(attributePaths = "connections")
    List<AiProvider> findAllByOrderByNameAsc();
}
