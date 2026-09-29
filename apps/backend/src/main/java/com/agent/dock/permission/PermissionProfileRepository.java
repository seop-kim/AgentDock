package com.agent.dock.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionProfileRepository extends JpaRepository<PermissionProfile, Long> {
    List<PermissionProfile> findAllByOrderByNameAsc();
}
