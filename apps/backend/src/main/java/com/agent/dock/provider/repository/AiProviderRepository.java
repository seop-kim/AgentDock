package com.agent.dock.provider.repository;

import com.agent.dock.provider.domain.AiProvider;
import com.agent.dock.provider.domain.ProviderKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {
    List<AiProvider> findByDeletedAtIsNullOrderByNameAsc();

    List<AiProvider> findByEnabledTrueAndDeletedAtIsNullOrderByNameAsc();

    Optional<AiProvider> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByKeyAndDeletedAtIsNull(ProviderKey key);
}
