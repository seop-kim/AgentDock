package com.agent.dock.provider;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {
    @EntityGraph(attributePaths = "connections")
    List<AiProvider> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "connections")
    List<AiProvider> findByDeletedAtIsNullOrderByNameAsc();

    Optional<AiProvider> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByKeyAndDeletedAtIsNull(ProviderKey key);
}
