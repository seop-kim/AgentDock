package com.agent.dock.provider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiConnectionRepository extends JpaRepository<AiConnection, Long> {
    List<AiConnection> findByProviderId(Long providerId);

    List<AiConnection> findByProviderIdIn(Collection<Long> providerIds);

    boolean existsByProviderId(Long providerId);

    void deleteByProviderId(Long providerId);

    @Query("select c from AiConnection c join fetch c.provider where c.id = :id")
    Optional<AiConnection> findWithProvider(@Param("id") Long id);
}
