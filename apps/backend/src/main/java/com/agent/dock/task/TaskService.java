package com.agent.dock.task;

import com.agent.dock.agent.Agent;
import com.agent.dock.agent.AgentRepository;
import com.agent.dock.common.BadRequestException;
import com.agent.dock.common.NotFoundException;
import com.agent.dock.group.AgentGroup;
import com.agent.dock.group.AgentGroupRepository;
import com.agent.dock.project.Project;
import com.agent.dock.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AgentGroupRepository groupRepository;
    private final AgentRepository agentRepository;

    public List<TaskResponse> findAll(Long projectId, Long groupId) {
        List<Task> tasks;
        if (groupId != null) {
            tasks = taskRepository.findByGroupWithRelations(groupId);
        } else if (projectId != null) {
            tasks = taskRepository.findByProjectWithRelations(projectId);
        } else {
            tasks = taskRepository.findAllWithRelations();
        }
        return tasks.stream().map(TaskResponse::from).toList();
    }

    public TaskResponse findOne(Long id) {
        Task task = taskRepository.findWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Task %d not found".formatted(id)));
        return TaskResponse.from(task);
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
}
