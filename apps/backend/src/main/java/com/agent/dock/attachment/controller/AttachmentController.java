package com.agent.dock.attachment.controller;

import com.agent.dock.attachment.dto.AttachmentResponse;
import com.agent.dock.attachment.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 첨부 창이 쓰는 두 가지: 워크스페이스 폴더 안 파일 목록과, 파일 업로드(첨부 폴더로 복사).
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService service;

    @GetMapping("/files")
    public List<String> files(@PathVariable Long workspaceId, @RequestParam(required = false) String path) {
        return service.listFiles(workspaceId, path);
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<AttachmentResponse> upload(@PathVariable Long workspaceId,
                                           @RequestParam("files") List<MultipartFile> files) {
        return service.upload(workspaceId, files);
    }

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId, @PathVariable Long id) {
        service.delete(id);
    }
}
