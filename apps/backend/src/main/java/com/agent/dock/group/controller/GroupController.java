package com.agent.dock.group.controller;

import com.agent.dock.group.dto.AddGroupMemberRequest;
import com.agent.dock.group.dto.CreateGroupRequest;
import com.agent.dock.group.dto.GroupResponse;
import com.agent.dock.group.dto.UpdateGroupRequest;
import com.agent.dock.group.service.GroupService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService service;

    @GetMapping
    public List<GroupResponse> findAll(@RequestParam(required = false) Long projectId) {
        return service.findAll(projectId);
    }

    @GetMapping("/{id}")
    public GroupResponse findOne(@PathVariable Long id) {
        return service.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@Valid @RequestBody CreateGroupRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public GroupResponse update(@PathVariable Long id, @Valid @RequestBody UpdateGroupRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse addMember(@PathVariable Long id, @Valid @RequestBody AddGroupMemberRequest request) {
        return service.addMember(id, request.agentId());
    }

    @DeleteMapping("/{id}/members/{agentId}")
    public GroupResponse removeMember(@PathVariable Long id, @PathVariable Long agentId) {
        return service.removeMember(id, agentId);
    }
}
