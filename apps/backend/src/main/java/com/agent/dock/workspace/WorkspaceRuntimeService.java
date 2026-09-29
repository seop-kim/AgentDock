package com.agent.dock.workspace;

import com.agent.dock.common.NotFoundException;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.AiProviderRepository;
import com.agent.dock.provider.AiRuntimeProbe;
import com.agent.dock.provider.ConnectionStatus;
import com.agent.dock.provider.ProbeRegistry;
import com.agent.dock.provider.ProbeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 폴더별 런타임 상태. 로그인은 런타임 전역이지만 "이 폴더에서 실제로 실행되는가"는 폴더마다 다를 수 있어
 * 그 폴더를 작업 디렉터리로 CLI 를 한 번 실행해 확인한다.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceRuntimeService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceRuntimeStatusRepository statusRepository;
    private final AiProviderRepository providerRepository;
    private final ProbeRegistry probeRegistry;

    /** 켜져 있는 런타임 목록과 이 폴더에서의 상태. */
    public List<WorkspaceRuntimeResponse> findAll(Long workspaceId) {
        findWorkspace(workspaceId);
        Map<Long, WorkspaceRuntimeStatus> byProvider = statusRepository.findByWorkspaceId(workspaceId).stream()
                .collect(Collectors.toMap(status -> status.getProvider().getId(), status -> status));
        return providerRepository.findByEnabledTrueAndDeletedAtIsNullOrderByNameAsc().stream()
                .map(provider -> WorkspaceRuntimeResponse.from(provider, byProvider.get(provider.getId())))
                .toList();
    }

    /** 이 폴더를 작업 디렉터리로 CLI 를 실제 실행해 확인하고 결과를 저장한다. 오래 걸리므로 트랜잭션으로 감싸지 않는다. */
    public WorkspaceRuntimeResponse check(Long workspaceId, Long providerId) {
        Workspace workspace = findWorkspace(workspaceId);
        AiProvider provider = providerRepository.findByIdAndDeletedAtIsNull(providerId)
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(providerId)));

        WorkspaceRuntimeStatus status = statusRepository.findByWorkspaceIdAndProviderId(workspaceId, providerId)
                .orElse(null);
        if (status == null) {
            status = new WorkspaceRuntimeStatus();
            status.setWorkspace(workspaceRepository.getReferenceById(workspaceId));
            status.setProvider(provider);
        }

        AiRuntimeProbe probe = probeRegistry.find(provider.getKey()).orElse(null);
        if (probe == null) {
            status.setStatus(ConnectionStatus.ERROR);
            status.setLastError("이 런타임은 아직 확인을 지원하지 않습니다: " + provider.getKey());
        } else {
            ProbeResult result = probe.check(workspace.getPath());
            status.setStatus(result.ok() ? ConnectionStatus.CONNECTED : ConnectionStatus.ERROR);
            status.setLastError(result.ok() ? null : result.detail());
        }
        status.setLastCheckedAt(Instant.now());
        return WorkspaceRuntimeResponse.from(provider, statusRepository.save(status));
    }

    private Workspace findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace %d not found".formatted(workspaceId)));
    }
}
