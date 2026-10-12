import { useEffect, useState } from 'react';
import { featureNotice, useFeatureStore, type MergePolicy } from '../../lib/featureStore';
import shared from '../../styles/shared.module.css';
import styles from './Settings.module.css';

const POLICY_FIELDS: { key: keyof MergePolicy; label: string; hint: string }[] = [
  {
    key: 'autoMerge',
    label: '자동 병합',
    hint: '트리가 끝나고 메인 저장소가 깨끗하면 트리 브랜치를 되돌려 병합합니다.',
  },
  {
    key: 'autoCleanupWorktree',
    label: '워크트리 자동 정리',
    hint: '병합이 끝나면 이 트리의 워크트리 폴더를 지웁니다.',
  },
  {
    key: 'autoCleanupBranch',
    label: '브랜치 자동 삭제',
    hint: '병합이 끝나면 전용 브랜치(agentdock/exec-*)를 지웁니다.',
  },
  {
    key: 'requireCleanTarget',
    label: '깨끗할 때만 병합',
    hint: '메인 저장소에 커밋하지 않은 변경이 있으면 병합하지 않고 수동으로 넘깁니다.',
  },
];

const DEFAULT_POLICY: MergePolicy = {
  autoMerge: true,
  autoCleanupWorktree: false,
  autoCleanupBranch: false,
  requireCleanTarget: true,
};

/**
 * 설정 > 병합 정책(GET/PUT /settings/merge-policy).
 * 워크트리 격리 실행이 끝난 뒤 자동 병합·정리를 어떻게 할지 정한다. API 가 아직 없으면 미지원 문구를 남긴다.
 */
export default function MergePolicySettings() {
  const { mergePolicy, mergePolicyStatus, loadMergePolicy, saveMergePolicy } = useFeatureStore();
  const [draft, setDraft] = useState<MergePolicy>(DEFAULT_POLICY);

  useEffect(() => {
    loadMergePolicy();
  }, []);

  useEffect(() => {
    if (mergePolicy !== null) setDraft(mergePolicy);
  }, [mergePolicy]);

  const ready = mergePolicyStatus === 'ready';
  const notice = featureNotice(mergePolicyStatus);

  const toggle = (key: keyof MergePolicy) => setDraft((prev) => ({ ...prev, [key]: !prev[key] }));
  return (
    <div>
      <div className={styles.sectionHeader}>
        <h1>병합 정책</h1>
        <button type="button" onClick={() => saveMergePolicy(draft)} disabled={!ready}>
          저장
        </button>
      </div>
      <p className={shared.hint}>
        명령 하나(트리)는 git worktree 에서 돌고, 끝나면 트리 브랜치에 커밋해 메인 저장소로 되돌립니다. 여기서 그
        뒤처리(자동 병합·정리)를 정합니다.
      </p>

      {notice !== null && <p className={shared.muted}>{notice}</p>}

      <section className={styles.card}>
        {POLICY_FIELDS.map((field) => (
          <label key={field.key} className={styles.policyRow}>
            <input
              type="checkbox"
              checked={draft[field.key]}
              onChange={() => toggle(field.key)}
              disabled={!ready}
            />
            <span className={styles.policyLabel}>{field.label}</span>
            <span className={shared.muted}>{field.hint}</span>
          </label>
        ))}
      </section>
    </div>
  );
}