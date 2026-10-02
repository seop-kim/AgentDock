package com.agent.dock.attachment.service;

import com.agent.dock.attachment.domain.Attachment;
import com.agent.dock.attachment.dto.AttachmentResponse;
import com.agent.dock.attachment.repository.AttachmentRepository;
import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.workspace.domain.Workspace;
import com.agent.dock.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 파일 첨부.
 *
 * 에이전트는 워크스페이스 폴더 밖을 볼 수 없으므로, 올린 파일을 **프로젝트 폴더 안
 * `.agentdock/attachments` 로 복사**하고 그 상대 경로를 실행 프롬프트에 적어 준다.
 * 저장 이름은 `<uuid 앞 8자>-원래이름` 이라 같은 이름을 여러 번 올려도 충돌하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    /** 첨부를 복사해 두는 폴더(워크스페이스 기준 상대 경로). 화면 안내 문구와 같아야 한다. */
    public static final String ATTACHMENT_FOLDER = ".agentdock/attachments";

    /**
     * 실행 프롬프트 끝에 붙이는 첨부 안내 절의 머리말. 위임이 이 절을 지시 파일의 "첨부 파일" 로 떼어 내므로
     * (거기서 참조) 머리말을 한 곳에서만 정의한다.
     */
    public static final String ATTACHMENT_PROMPT_HEADER = "[첨부 파일]";

    private static final int MAX_FILES = 2000;
    private static final int MAX_DEPTH = 8;
    /** 파일 목록에서 건너뛸 폴더(용량이 크고 첨부와 무관하다). */
    private static final Set<String> SKIPPED_DIRS =
            Set.of(".git", "node_modules", "build", "dist", "target", ".gradle", ".idea", ".next", "out");

    private final AttachmentRepository attachmentRepository;
    private final WorkspaceRepository workspaceRepository;
    /** 전역 SSE 스트림에 "첨부가 바뀌었다"를 알린다(파일 목록·Task 연결). */
    private final EventPublisher changeEvents;

    /** 첨부 창에 보여 줄 파일 목록(워크스페이스 기준 상대 경로). */
    public List<String> listFiles(Long workspaceId, String subPath) {
        Workspace workspace = requireWorkspace(workspaceId);
        Path root = rootOf(workspace);
        Path base = subPath == null || subPath.isBlank() ? root : root.resolve(subPath).normalize();
        if (!base.startsWith(root)) {
            throw new BadRequestException("워크스페이스 밖은 볼 수 없습니다");
        }
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(base, MAX_DEPTH)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> !isInsideSkippedFolder(root, path))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .sorted(Comparator.naturalOrder())
                    .limit(MAX_FILES)
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("파일 목록을 읽지 못했습니다: " + ex.getMessage(), ex);
        }
    }

    /** 올린 파일을 워크스페이스 안 첨부 폴더로 복사하고 첨부로 기록한다. */
    public List<AttachmentResponse> upload(Long workspaceId, List<MultipartFile> files) {
        Workspace workspace = requireWorkspace(workspaceId);
        Path root = rootOf(workspace);
        Path folder = root.resolve(ATTACHMENT_FOLDER).normalize();
        try {
            Files.createDirectories(folder);
        } catch (IOException ex) {
            throw new IllegalStateException("첨부 폴더를 만들지 못했습니다: " + ex.getMessage(), ex);
        }

        List<AttachmentResponse> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String original = safeName(file.getOriginalFilename());
            String stored = ATTACHMENT_FOLDER + "/" + UUID.randomUUID().toString().substring(0, 8) + "-" + original;
            Path target = root.resolve(stored).normalize();
            if (!target.startsWith(folder)) {
                throw new BadRequestException("잘못된 파일 이름입니다: " + original);
            }
            try {
                file.transferTo(target);
            } catch (IOException ex) {
                throw new IllegalStateException("파일을 복사하지 못했습니다: " + original, ex);
            }

            Attachment attachment = new Attachment();
            attachment.setWorkspace(workspace);
            attachment.setOriginalName(original);
            attachment.setStoredPath(stored);
            attachment.setSizeBytes(file.getSize());
            saved.add(AttachmentResponse.from(attachmentRepository.save(attachment)));
        }
        // 파일 목록이 바뀌었다(워크스페이스 단위라 프로젝트를 모른다 — projectId 는 null).
        changeEvents.attachmentChanged(null, null);
        return saved;
    }

    /**
     * 명령에 붙은 첨부를 태스크에 연결하고, 실행 프롬프트에 붙일 문장을 돌려준다.
     * 첨부가 없으면 빈 문자열이라 프롬프트가 그대로다.
     */
    @Transactional
    public String attachToTask(Long taskId, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return "";
        }
        List<Attachment> attachments = attachmentRepository.findByIdInOrderByIdAsc(attachmentIds);
        if (attachments.isEmpty()) {
            return "";
        }
        attachments.forEach(attachment -> attachment.setTaskId(taskId));
        attachmentRepository.saveAll(attachments);
        changeEvents.attachmentChanged(null, taskId);

        StringBuilder prompt = new StringBuilder();
        prompt.append("\n\n").append(ATTACHMENT_PROMPT_HEADER)
                .append("\n아래 경로는 프로젝트 폴더 기준입니다. 이 경로로 파일을 읽으세요.\n");
        for (Attachment attachment : attachments) {
            prompt.append("- ").append(attachment.getStoredPath())
                    .append(" (").append(attachment.getOriginalName()).append(")\n");
        }
        return prompt.toString();
    }

    /** 워크스페이스 폴더가 사라졌거나 잘못됐을 때를 대비한 기준 경로. */
    private Path rootOf(Workspace workspace) {
        Path root = Path.of(workspace.getPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new BadRequestException("워크스페이스 폴더를 찾을 수 없습니다: " + workspace.getPath());
        }
        return root;
    }

    private Workspace requireWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace %d not found".formatted(workspaceId)));
    }

    /** 파일 이름에서 경로를 떼어 낸다(경로 조작 차단). 테스트는 같은 패키지라 여기서 직접 확인한다. */
    String safeName(String originalName) {
        String name = originalName == null || originalName.isBlank() ? "file" : originalName;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        return name.isEmpty() ? "file" : name;
    }

    private boolean isInsideSkippedFolder(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path part : relative) {
            if (SKIPPED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }
}
