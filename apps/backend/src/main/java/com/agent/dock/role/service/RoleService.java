package com.agent.dock.role.service;

import com.agent.dock.role.domain.AgentRole;
import com.agent.dock.role.dto.AgentRoleResponse;
import com.agent.dock.role.repository.AgentRoleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final AgentRoleRepository repository;

    public List<AgentRoleResponse> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(AgentRoleResponse::from).toList();
    }
}
