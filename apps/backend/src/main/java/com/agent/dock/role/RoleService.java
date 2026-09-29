package com.agent.dock.role;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final AgentRoleRepository repository;

    public List<AgentRoleResponse> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(AgentRoleResponse::from).toList();
    }

    public AgentRoleResponse create(CreateAgentRoleRequest request) {
        AgentRole role = new AgentRole();
        role.setName(request.name());
        role.setDescription(request.description());
        return AgentRoleResponse.from(repository.save(role));
    }
}
