package com.agent.dock.settings.service;

import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.settings.domain.MergePolicy;
import com.agent.dock.settings.dto.MergePolicyRequest;
import com.agent.dock.settings.dto.MergePolicyResponse;
import com.agent.dock.settings.repository.MergePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 병합 정책(전역 싱글턴) 조회·갱신. 한 행(id=1)만 있고, 없으면 기본값으로 방어 생성한다.
 *
 * <p>값이 바뀌면 전역 스트림에 {@code settings.changed} 를 알린다 — 화면은 설정 조각(병합 정책)만 다시 읽는다.
 * 발행은 서비스 계층에서만 한다.
 */
@Service
@RequiredArgsConstructor
public class MergePolicyService {

    private final MergePolicyRepository policyRepository;
    /** 전역 SSE 스트림에 "설정이 바뀌었다"를 알린다. */
    private final EventPublisher changeEvents;

    /** 현재 정책(없으면 기본값으로 한 행을 만들어 돌려준다 — 싱글턴은 항상 존재한다). */
    @Transactional
    public MergePolicyResponse get() {
        return MergePolicyResponse.from(require());
    }

    /** 정책을 통째로 교체한다(전체 교체 — 두 값 모두 필수). 바뀌면 {@code settings.changed} 를 발행한다. */
    @Transactional
    public MergePolicyResponse update(MergePolicyRequest request) {
        MergePolicy policy = require();
        policy.setAutoCleanupEnabled(request.autoCleanupEnabled());
        policy.setCleanupDeleteBranch(request.cleanupDeleteBranch());
        MergePolicy saved = policyRepository.save(policy);
        changeEvents.settingsChanged();
        return MergePolicyResponse.from(saved);
    }

    /** 싱글턴 한 행을 읽고, 없으면(시드 누락·수동 삭제) 기본값으로 만들어 저장한다. */
    private MergePolicy require() {
        return policyRepository.findById(MergePolicy.SINGLETON_ID)
                .orElseGet(() -> policyRepository.save(new MergePolicy()));
    }
}