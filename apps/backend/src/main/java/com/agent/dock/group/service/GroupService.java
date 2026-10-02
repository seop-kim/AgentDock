package com.agent.dock.group.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.dto.AgentSummary;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.attachment.repository.AttachmentRepository;
import com.agent.dock.common.exception.ConflictException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.group.domain.AgentGroup;
import com.agent.dock.group.domain.AgentGroupMember;
import com.agent.dock.group.dto.CreateGroupRequest;
import com.agent.dock.group.dto.GroupResponse;
import com.agent.dock.group.dto.UpdateGroupRequest;
import com.agent.dock.group.repository.AgentGroupMemberRepository;
import com.agent.dock.group.repository.AgentGroupRepository;
import com.agent.dock.project.domain.Project;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.task.repository.TaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {
    private final AgentGroupRepository groupRepository;
    private final AgentGroupMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final AgentRepository agentRepository;
    private final TaskRepository taskRepository;
    private final ExecutionRepository executionRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final AttachmentRepository attachmentRepository;
    /** 전역 SSE 스트림에 "그룹(팀)이 바뀌었다"를 알린다. */
    private final EventPublisher changeEvents;

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
        changeEvents.groupChanged(request.projectId());
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
        group.setPrompt(request.prompt() == null ? "" : request.prompt());
        group.setLeaderAgent(request.leaderAgentId() == null ? null : requireAgent(request.leaderAgentId()));
        groupRepository.save(group);
        changeEvents.groupChanged(projectId);
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
        changeEvents.groupChanged(group.getProjectId());
        return findOne(groupId);
    }

    public GroupResponse removeMember(Long groupId, Long agentId) {
        if (!groupRepository.existsById(groupId)) {
            throw new NotFoundException("Group %d not found".formatted(groupId));
        }
        AgentGroupMember member = memberRepository.findMember(groupId, agentId)
                .orElseThrow(() -> new NotFoundException("Agent %d is not a member of group %d".formatted(agentId, groupId)));
        memberRepository.delete(member);
        changeEvents.groupChanged(groupProjectId(groupId));
        return findOne(groupId);
    }

    /** 그룹의 프로젝트 id(알림에 싣는다). 못 찾으면 null. */
    private Long groupProjectId(Long groupId) {
        return groupRepository.findById(groupId).map(AgentGroup::getProjectId).orElse(null);
    }

    /**
     * 그룹(팀) 삭제. FK 안전 순서: 이 그룹을 담당으로 가진 Task 와 그 실행·로그·첨부 → 멤버 → 그룹.
     */
    @Transactional
    public void delete(Long id) {
        AgentGroup group = groupRepository.findWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Group %d not found".formatted(id)));
        List<Long> taskIds = taskRepository.findIdsByGroupId(id);
        if (!taskIds.isEmpty()) {
            List<Long> executionIds = executionRepository.findIdsByTaskIdIn(taskIds);
            if (!executionIds.isEmpty()) {
                executionLogRepository.deleteByExecutionIdIn(executionIds);
                executionRepository.deleteAllByIdInBatch(executionIds);
            }
            attachmentRepository.deleteByTaskIdIn(taskIds);
            taskRepository.deleteAllByIdInBatch(taskIds);
        }
        memberRepository.deleteByGroupIdIn(List.of(id));
        groupRepository.delete(group);
        changeEvents.groupChanged(group.getProjectId());
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
