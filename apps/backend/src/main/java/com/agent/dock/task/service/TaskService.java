package com.agent.dock.task.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.ConflictException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.delegation.service.DelegationService;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.dto.ExecutionFinishedEvent;
import com.agent.dock.execution.dto.ExecutionResponse;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.group.domain.AgentGroup;
import com.agent.dock.group.repository.AgentGroupRepository;
import com.agent.dock.project.domain.Project;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.task.domain.Task;
import com.agent.dock.task.domain.TaskStatus;
import com.agent.dock.task.dto.CommandRequest;
import com.agent.dock.task.dto.CreateTaskRequest;
import com.agent.dock.task.dto.TaskResponse;
import com.agent.dock.task.repository.TaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AgentGroupRepository groupRepository;
    private final AgentRepository agentRepository;
    private final ExecutionRepository executionRepository;
    private final DelegationService delegationService;

    public List<TaskResponse> findAll(Long projectId, Long groupId) {
        List<Task> tasks;
        if (groupId != null) {
            tasks = taskRepository.findByGroupWithRelations(groupId);
        } else if (projectId != null) {
            tasks = taskRepository.findByProjectWithRelations(projectId);
        } else {
            tasks = taskRepository.findAllWithRelations();
        }
        return tasks.stream().map(this::toResponse).toList();
    }

    public TaskResponse findOne(Long id) {
        Task task = taskRepository.findWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Task %d not found".formatted(id)));
        return toResponse(task);
    }

    public TaskResponse create(CreateTaskRequest request) {
        if ((request.groupId() == null) == (request.agentId() == null)) {
            throw new BadRequestException("exactly one of groupId or agentId is required");
        }
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(request.projectId())));

        Task task = new Task();
        task.setProject(project);
        task.setTitle(request.title());
        task.setPrompt(request.prompt());
        task.setStatus(TaskStatus.CREATED);

        if (request.groupId() != null) {
            AgentGroup group = groupRepository.findById(request.groupId())
                    .orElseThrow(() -> new NotFoundException("Group %d not found".formatted(request.groupId())));
            if (!group.getProject().getId().equals(project.getId())) {
                throw new BadRequestException("Group does not belong to project");
            }
            task.setGroup(group);
        } else {
            Agent agent = agentRepository.findById(request.agentId())
                    .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(request.agentId())));
            task.setAgent(agent);
        }

        Task saved = taskRepository.save(task);
        return findOne(saved.getId());
    }

    /** 할당 대상(그룹이면 리더)에게 실행을 맡긴다. 판단이 필요한 대상이면 그 아래로 다시 위임된다. */
    public ExecutionResponse run(Long id) {
        Task task = taskRepository.findWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Task %d not found".formatted(id)));

        Long assigneeAgentId = resolveAssigneeAgentId(task);
        Long projectId = task.getProject().getId();

        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(task);
        try {
            Execution root = delegationService.start(projectId, assigneeAgentId, null, task.getPrompt(), task.getId());
            return ExecutionResponse.from(root);
        } catch (RuntimeException ex) {
            // 실행이 만들어지지 않았으므로 상태를 되돌린다.
            task.setStatus(TaskStatus.CREATED);
            taskRepository.save(task);
            throw ex;
        }
    }

    /**
     * 프로젝트 채팅 명령: Task 를 만들고 실행 트리를 시작한다. 대상이 없으면 프로젝트 마스터가 받는다.
     * 채팅 기록은 Task 로 표현한다(사용자 말풍선 = title, 마스터 응답 = 루트 실행의 result_text).
     */
    public Execution command(Long projectId, CommandRequest request) {
        if (request.targetAgentId() != null && request.groupId() != null) {
            throw new BadRequestException("targetAgentId 와 groupId 는 함께 쓸 수 없습니다");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(projectId)));

        Task task = new Task();
        task.setProject(project);
        task.setTitle(titleOf(request.text()));
        task.setPrompt(request.text());
        task.setStatus(TaskStatus.CREATED);
        if (request.targetAgentId() != null) {
            task.setAgent(requireAgent(request.targetAgentId()));
        } else if (request.groupId() != null) {
            AgentGroup group = groupRepository.findById(request.groupId())
                    .orElseThrow(() -> new NotFoundException("Group %d not found".formatted(request.groupId())));
            if (!group.getProject().getId().equals(projectId)) {
                throw new BadRequestException("Group does not belong to project");
            }
            task.setGroup(group);
        } else {
            task.setAgent(requireAgent(requireMasterAgentId(project)));
        }
        Task saved = taskRepository.save(task);

        try {
            Execution root = delegationService.start(projectId, request.targetAgentId(), request.groupId(),
                    request.text(), saved.getId());
            saved.setStatus(TaskStatus.IN_PROGRESS);
            taskRepository.save(saved);
            return root;
        } catch (RuntimeException ex) {
            saved.setStatus(TaskStatus.FAILED);
            taskRepository.save(saved);
            throw ex;
        }
    }

    private Long requireMasterAgentId(Project project) {
        if (project.getMasterAgentId() == null) {
            throw new ConflictException("프로젝트에 마스터 에이전트가 지정되어 있지 않습니다");
        }
        return project.getMasterAgentId();
    }

    private Agent requireAgent(Long agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(agentId)));
    }

    /** 채팅 기록에 남길 한 줄 제목. */
    private String titleOf(String text) {
        String singleLine = text.strip().replaceAll("\\s+", " ");
        return singleLine.length() <= 60 ? singleLine : singleLine.substring(0, 60) + "…";
    }

    @EventListener
    public void onExecutionFinished(ExecutionFinishedEvent event) {
        if (event.taskId() == null) {
            return;
        }
        taskRepository.findById(event.taskId()).ifPresent(task -> {
            task.setStatus(event.status() == ExecutionStatus.SUCCEEDED ? TaskStatus.SUCCEEDED : TaskStatus.FAILED);
            taskRepository.save(task);
        });
    }

    private Long resolveAssigneeAgentId(Task task) {
        if (task.getAgent() != null) {
            return task.getAgent().getId();
        }
        AgentGroup group = task.getGroup();
        if (group == null) {
            throw new BadRequestException("Task has no assignee");
        }
        if (group.getLeaderAgent() == null) {
            throw new BadRequestException("Group has no leader assigned");
        }
        return group.getLeaderAgent().getId();
    }

    private TaskResponse toResponse(Task task) {
        Long latestExecutionId = executionRepository.findFirstByTaskIdOrderByIdDesc(task.getId())
                .map(execution -> execution.getId())
                .orElse(null);
        return TaskResponse.from(task, latestExecutionId);
    }
}
