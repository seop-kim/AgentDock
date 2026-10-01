package com.agent.dock.task.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.attachment.service.AttachmentService;
import com.agent.dock.delegation.service.DelegationService;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.group.repository.AgentGroupRepository;
import com.agent.dock.project.domain.Project;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.task.domain.Task;
import com.agent.dock.task.dto.CommandRequest;
import com.agent.dock.task.repository.TaskRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    @Mock AgentGroupRepository groupRepository;
    @Mock AgentRepository agentRepository;
    @Mock ExecutionRepository executionRepository;
    @Mock DelegationService delegationService;
    @Mock AttachmentService attachmentService;
    @InjectMocks TaskService service;

    private Project project;
    private Agent agent;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setId(2L);
        project.setName("P2");
        agent = new Agent();
        agent.setId(7L);
        agent.setName("Dev");
        agent.setProject(project);
    }

    @Test
    void commandPassesAttachmentNoteIntoDelegationPrompt() {
        when(projectRepository.findById(2L)).thenReturn(Optional.of(project));
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(30L);
            return task;
        });
        String note = "\n\n[첨부 파일]\n아래 경로는 프로젝트 폴더 기준입니다. 이 경로로 파일을 읽으세요.\n"
                + "- .agentdock/attachments/abcd1234-report.pdf (report.pdf)\n";
        when(attachmentService.attachToTask(30L, List.of(11L))).thenReturn(note);
        Execution root = new Execution();
        root.setId(100L);
        root.setTaskId(30L);
        when(delegationService.start(eq(2L), eq(7L), isNull(), anyString(), eq(30L))).thenReturn(root);

        Execution result = service.command(2L, new CommandRequest("버그 고쳐줘", 7L, null, List.of(11L)));

        assertThat(result.getId()).isEqualTo(100L);
        verify(attachmentService).attachToTask(30L, List.of(11L));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(delegationService).start(eq(2L), eq(7L), isNull(), prompt.capture(), eq(30L));
        assertThat(prompt.getValue())
                .startsWith("버그 고쳐줘")
                .contains("[첨부 파일]")
                .contains(".agentdock/attachments/abcd1234-report.pdf");
    }

    @Test
    void commandKeepsRawUserTextOnTheTask() {
        when(projectRepository.findById(2L)).thenReturn(Optional.of(project));
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(30L);
            return task;
        });
        when(attachmentService.attachToTask(30L, null)).thenReturn("");
        Execution root = new Execution();
        root.setId(100L);
        root.setTaskId(30L);
        when(delegationService.start(eq(2L), eq(7L), isNull(), anyString(), eq(30L))).thenReturn(root);

        service.command(2L, new CommandRequest("버그 고쳐줘", 7L, null, null));

        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getPrompt()).isEqualTo("버그 고쳐줘");
        assertThat(saved.getAllValues().get(0).getTitle()).isEqualTo("버그 고쳐줘");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(delegationService).start(eq(2L), eq(7L), isNull(), prompt.capture(), eq(30L));
        assertThat(prompt.getValue()).isEqualTo("버그 고쳐줘");
    }
}
