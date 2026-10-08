package com.agent.dock.attachment.service;

import com.agent.dock.attachment.domain.Attachment;
import com.agent.dock.attachment.repository.AttachmentRepository;
import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.workspace.domain.Workspace;
import com.agent.dock.workspace.repository.WorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
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

    @Test
    void deleteRemovesRecordAndCopiedFile(@TempDir Path workspaceDir) throws Exception {
        Path stored = workspaceDir.resolve(AttachmentService.ATTACHMENT_FOLDER).resolve("abcd1234-report.pdf");
        Files.createDirectories(stored.getParent());
        Files.writeString(stored, "contents");
        Attachment attachment = attachmentFor(workspaceDir, 11L,
                AttachmentService.ATTACHMENT_FOLDER + "/abcd1234-report.pdf");
        when(attachmentRepository.findById(11L)).thenReturn(Optional.of(attachment));

        service.delete(11L);

        assertThat(stored).doesNotExist();
        verify(attachmentRepository).delete(attachment);
    }

    @Test
    void deleteBlocksCopiedFileOutsideWorkspaceAndKeepsIt(@TempDir Path tempDir) throws Exception {
        Path workspaceDir = Files.createDirectory(tempDir.resolve("ws"));
        Path outside = Files.writeString(tempDir.resolve("secret.txt"), "top secret");
        Attachment attachment = attachmentFor(workspaceDir, 12L, "../secret.txt");
        when(attachmentRepository.findById(12L)).thenReturn(Optional.of(attachment));

        assertThatThrownBy(() -> service.delete(12L))
                .isInstanceOf(BadRequestException.class);

        assertThat(outside).exists();
        verify(attachmentRepository, never()).delete(any(Attachment.class));
    }

    @Test
    void deleteMissingIdThrowsNotFound() {
        when(attachmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(NotFoundException.class);

        verify(attachmentRepository, never()).delete(any(Attachment.class));
    }

    private Attachment attachmentFor(Path workspaceDir, Long id, String storedPath) {
        Workspace workspace = new Workspace();
        workspace.setId(7L);
        workspace.setPath(workspaceDir.toString());

        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setWorkspace(workspace);
        attachment.setWorkspaceId(7L);
        attachment.setTaskId(5L);
        attachment.setOriginalName("report.pdf");
        attachment.setStoredPath(storedPath);
        return attachment;
    }
}