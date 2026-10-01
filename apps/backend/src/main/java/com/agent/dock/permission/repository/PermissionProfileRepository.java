package com.agent.dock.permission.repository;

import com.agent.dock.permission.domain.PermissionProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionProfileRepository extends JpaRepository<PermissionProfile, Long> {
    List<PermissionProfile> findAllByOrderByNameAsc();
}
