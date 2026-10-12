package com.agent.dock.settings.service;

import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.settings.domain.MergePolicy;
import com.agent.dock.settings.dto.MergePolicyRequest;
import com.agent.dock.settings.dto.MergePolicyResponse;
import com.agent.dock.settings.repository.MergePolicyRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 병합 정책(전역 싱글턴) 조회·갱신. 행이 없으면 기본값으로 방어 생성하고, 갱신하면 settings.changed 를 발행한다.
 */
@ExtendWith(MockitoExtension.class)
class MergePolicyServiceTest {

    @Mock MergePolicyRepository policyRepository;
    @Mock EventPublisher changeEvents;
    @InjectMocks MergePolicyService service;

    @Test
    void createsADefaultRowWhenMissing() {
        when(policyRepository.findById(MergePolicy.SINGLETON_ID)).thenReturn(Optional.empty());
        when(policyRepository.save(any(MergePolicy.class))).thenAnswer(call -> call.getArgument(0));

        MergePolicyResponse response = service.get();

        assertThat(response.autoCleanupEnabled()).isFalse();
        assertThat(response.cleanupDeleteBranch()).isTrue();
        verify(policyRepository).save(any(MergePolicy.class));
    }

    @Test
    void returnsTheStoredPolicy() {
        MergePolicy policy = policy(true, false);
        when(policyRepository.findById(MergePolicy.SINGLETON_ID)).thenReturn(Optional.of(policy));

        MergePolicyResponse response = service.get();

        assertThat(response.autoCleanupEnabled()).isTrue();
        assertThat(response.cleanupDeleteBranch()).isFalse();
    }

    @Test
    void updatesThePolicyAndPublishesSettingsChanged() {
        MergePolicy policy = policy(false, true);
        when(policyRepository.findById(MergePolicy.SINGLETON_ID)).thenReturn(Optional.of(policy));
        when(policyRepository.save(any(MergePolicy.class))).thenAnswer(call -> call.getArgument(0));

        MergePolicyResponse response = service.update(new MergePolicyRequest(true, false));

        assertThat(response.autoCleanupEnabled()).isTrue();
        assertThat(response.cleanupDeleteBranch()).isFalse();
        verify(changeEvents).settingsChanged();
    }

    @Test
    void rejectsNullValues() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<MergePolicyRequest>> violations =
                validator.validate(new MergePolicyRequest(null, null));

        assertThat(violations).hasSize(2);
    }

    private static MergePolicy policy(boolean cleanup, boolean deleteBranch) {
        MergePolicy policy = new MergePolicy();
        policy.setAutoCleanupEnabled(cleanup);
        policy.setCleanupDeleteBranch(deleteBranch);
        return policy;
    }
}