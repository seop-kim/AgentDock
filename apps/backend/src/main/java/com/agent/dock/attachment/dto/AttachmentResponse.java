package com.agent.dock.attachment.dto;

import com.agent.dock.attachment.domain.Attachment;

/** 화면(첨부 칩·파일 목록)에 쓰는 첨부 정보. storedPath 는 워크스페이스 기준 상대 경로다. */
public record AttachmentResponse(
        Long id,
        Long workspaceId,
        Long taskId,
        String originalName,
        String storedPath,
        Long sizeBytes
) {
    public static AttachmentResponse from(Attachment attachment) {
        Long workspaceId = attachment.getWorkspaceId() != null
                ? attachment.getWorkspaceId()
                : (attachment.getWorkspace() == null ? null : attachment.getWorkspace().getId());
        return new AttachmentResponse(attachment.getId(), workspaceId, attachment.getTaskId(),
                attachment.getOriginalName(), attachment.getStoredPath(), attachment.getSizeBytes());
    }
}
