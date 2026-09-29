package com.agent.dock.provider;

import com.agent.dock.agent.AgentRepository;
import com.agent.dock.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AiConnectionService {
    private final AiConnectionRepository connectionRepository;
    private final ProbeRegistry probeRegistry;
    private final AgentRepository agentRepository;

    /** 연결 상태를 실제로 확인하고 결과를 저장한다. 자격증명은 CLI 세션에 위임하므로 여기서 다루지 않는다. */
    public AiConnectionResponse check(Long connectionId) {
        // provider 를 fetch join 으로 함께 읽는다(open-in-view=false 라 지연 로딩 불가)
        AiConnection connection = connectionRepository.findWithProvider(connectionId)
                .orElseThrow(() -> new NotFoundException("AiConnection %d not found".formatted(connectionId)));

        AiConnectionProbe probe = probeRegistry.find(connection.getProvider().getKey()).orElse(null);
        if (probe == null) {
            connection.setStatus(ConnectionStatus.ERROR);
            connection.setLastError("이 Provider 는 아직 연결 확인을 지원하지 않습니다: " + connection.getProvider().getKey());
        } else {
            ProbeResult result = probe.check();
            connection.setStatus(result.ok() ? ConnectionStatus.CONNECTED : ConnectionStatus.ERROR);
            connection.setLastError(result.ok() ? null : result.detail());
        }
        connection.setLastCheckedAt(Instant.now());
        return AiConnectionResponse.from(connectionRepository.save(connection));
    }

    /** Connection 을 지운다. 참조하던 Agent 는 연결이 끊긴 상태가 되어 실행 가드에 막힌다. */
    @Transactional
    public void delete(Long connectionId) {
        if (!connectionRepository.existsById(connectionId)) {
            throw new NotFoundException("AiConnection %d not found".formatted(connectionId));
        }
        agentRepository.detachConnection(connectionId);
        connectionRepository.deleteById(connectionId);
    }
}
