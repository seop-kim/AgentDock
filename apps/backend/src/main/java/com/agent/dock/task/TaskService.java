package com.agent.dock.task;

import com.agent.dock.agent.Agent;
import com.agent.dock.agent.AgentRepository;
import com.agent.dock.common.BadRequestException;
import com.agent.dock.common.NotFoundException;
import com.agent.dock.execution.ExecutionFinishedEvent;
import com.agent.dock.execution.ExecutionRepository;
import com.agent.dock.execution.ExecutionResponse;
import com.agent.dock.execution.ExecutionService;
import com.agent.dock.execution.ExecutionStatus;
import com.agent.dock.group.AgentGroup;
import com.agent.dock.group.AgentGroupRepository;
import com.agent.dock.project.Project;
import com.agent.dock.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AgentGroupRepository groupRepository;
    private final AgentRepository agentRepository;
    private final ExecutionRepository executionRepository;
    private final ExecutionService executionService;

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

    /** 할당 대상(그룹이면 리더)에게 실행을 위임한다. 작업 디렉터리는 프로젝트의 워크스페이스. */
    public ExecutionResponse run(Long id) {
        Task task = taskRepository.findWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Task %d not found".formatted(id)));

        Long assigneeAgentId = resolveAssigneeAgentId(task);
        Long projectId = task.getProject().getId();

        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(task);
        try {
            return executionService.create(assigneeAgentId, projectId, task.getPrompt(), task.getId());
        } catch (RuntimeException ex) {
            // 실행이 만들어지지 않았으므로 상태를 되돌린다.
            task.setStatus(TaskStatus.CREATED);
            taskRepository.save(task);
            throw ex;
        }
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
