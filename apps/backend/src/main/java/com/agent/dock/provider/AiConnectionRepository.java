package com.agent.dock.provider;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiConnectionRepository extends JpaRepository<AiConnection, Long> {
    List<AiConnection> findByProviderId(Long providerId);
}
