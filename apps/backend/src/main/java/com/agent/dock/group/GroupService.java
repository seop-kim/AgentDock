package com.agent.dock.group;

import com.agent.dock.agent.Agent;
import com.agent.dock.agent.AgentRepository;
import com.agent.dock.agent.AgentSummary;
import com.agent.dock.common.ConflictException;
import com.agent.dock.common.NotFoundException;
import com.agent.dock.project.Project;
import com.agent.dock.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {
    private final AgentGroupRepository groupRepository;
    private final AgentGroupMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final AgentRepository agentRepository;

    public List<GroupResponse> findAll(Long projectId) {
        List<AgentGroup> groups = projectId == null
                ? groupRepository.findAllWithRelations()
                : groupRepository.findByProjectWithRelations(projectId);
        return groups.stream().map(g -> GroupResponse.from(g, membersOf(g.getId()))).toList();
    }

    public GroupResponse findOne(Long id) {
        AgentGroup group = groupRepository.findWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Group %d not found".formatted(id)));
        return GroupResponse.from(group, membersOf(id));
    }

    public GroupResponse create(CreateGroupRequest request) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(request.projectId())));
        if (groupRepository.existsByProjectIdAndName(request.projectId(), request.name())) {
            throw new ConflictException("Group name already exists in this project");
        }
        AgentGroup group = new AgentGroup();
        group.setProject(project);
        group.setName(request.name());
        group.setDescription(request.description());
        if (request.leaderAgentId() != null) {
            group.setLeaderAgent(requireAgent(request.leaderAgentId()));
        }
        AgentGroup saved = groupRepository.save(group);
        return findOne(saved.getId());
    }

    public GroupResponse update(Long id, UpdateGroupRequest request) {
        AgentGroup group = groupRepository.findWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Group %d not found".formatted(id)));
        Long projectId = group.getProject().getId();
        if (!group.getName().equals(request.name()) && groupRepository.existsByProjectIdAndName(projectId, request.name())) {
            throw new ConflictException("Group name already exists in this project");
        }
        group.setName(request.name());
        group.setDescription(request.description());
        group.setLeaderAgent(request.leaderAgentId() == null ? null : requireAgent(request.leaderAgentId()));
        groupRepository.save(group);
        return findOne(id);
    }

    public GroupResponse addMember(Long groupId, Long agentId) {
        AgentGroup group = groupRepository.findWithRelations(groupId)
                .orElseThrow(() -> new NotFoundException("Group %d not found".formatted(groupId)));
        if (memberRepository.findMember(groupId, agentId).isPresent()) {
            throw new ConflictException("Agent is already a member of this group");
        }
        AgentGroupMember member = new AgentGroupMember();
        member.setGroup(group);
        member.setAgent(requireAgent(agentId));
        memberRepository.save(member);
        return findOne(groupId);
    }

    public GroupResponse removeMember(Long groupId, Long agentId) {
        if (!groupRepository.existsById(groupId)) {
            throw new NotFoundException("Group %d not found".formatted(groupId));
        }
        AgentGroupMember member = memberRepository.findMember(groupId, agentId)
                .orElseThrow(() -> new NotFoundException("Agent %d is not a member of group %d".formatted(agentId, groupId)));
        memberRepository.delete(member);
        return findOne(groupId);
    }

    private List<AgentSummary> membersOf(Long groupId) {
        return memberRepository.findMembersWithAgent(groupId).stream()
                .map(m -> AgentSummary.from(m.getAgent()))
                .toList();
    }

    private Agent requireAgent(Long agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(agentId)));
    }
}
