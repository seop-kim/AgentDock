package com.agent.dock.attachment.service;

import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.attachment.domain.Attachment;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.attachment.repository.AttachmentRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.workspace.repository.WorkspaceRepository;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {
    @Mock AttachmentRepository attachmentRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock EventPublisher changeEvents;
    @InjectMocks AttachmentService service;

    @Test
    void attachToTaskLinksAttachmentsAndBuildsPathNote() {
        Attachment attachment = new Attachment();
        attachment.setId(1L);
        attachment.setStoredPath(".agentdock/attachments/abcd1234-report.pdf");
        attachment.setOriginalName("report.pdf");
        when(attachmentRepository.findByIdInOrderByIdAsc(List.of(1L))).thenReturn(List.of(attachment));

        String prompt = service.attachToTask(5L, List.of(1L));

        assertThat(prompt).contains("[첨부 파일]");
        assertThat(prompt).contains("- .agentdock/attachments/abcd1234-report.pdf (report.pdf)");
        assertThat(attachment.getTaskId()).isEqualTo(5L);
        verify(attachmentRepository).saveAll(List.of(attachment));
    }

    @Test
    void attachToTaskWithoutIdsReturnsEmptyPrompt() {
        assertThat(service.attachToTask(5L, List.of())).isEmpty();
        assertThat(service.attachToTask(5L, null)).isEmpty();

        verifyNoInteractions(attachmentRepository, workspaceRepository);
    }

    @Test
    void safeNameReducesDirectoryTraversalToFileName() {
        assertThat(service.safeName("a/../../etc/passwd")).isEqualTo("passwd");
        assertThat(service.safeName("../../secret.txt")).isEqualTo("secret.txt");
        assertThat(service.safeName("C:\\Users\\me\\report.pdf")).isEqualTo("report.pdf");
        assertThat(service.safeName("plain.txt")).isEqualTo("plain.txt");
    }

    @Test
    void safeNameFallsBackForBlankOrMissingNames() {
        assertThat(service.safeName(null)).isEqualTo("file");
        assertThat(service.safeName("   ")).isEqualTo("file");
    }
}
