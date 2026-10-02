import { useState } from 'react';
import { ChevronDownIcon, ChevronUpIcon } from '../../components/icons';
import { useAgentDockStore } from '../../store/AgentDockStore';
import type { Project } from '../../types';
import waiting from './WaitingInput.module.css';

/**
 * 자동 병합이 막힌 트리(`mergeStatus=MANUAL`)를 화면 아래 가운데에서 **물어본다**.
 *
 * <p>메인 저장소에 변경이 있으면 자동 병합을 하지 않고 브랜치만 남겨 두는데, 그러면 사람이 그 사실을
 * 알아채지 못해 작업이 방치된다. 알약으로 알리고 "지금 병합"을 누르면 기존 다시-병합 경로
 * (`retryMerge`)로 바로 병합한다. 사람이 손으로 정리한 뒤 누르는 버튼이다.
 */
export default function MergeAttention({
  project,
  /** 입력 대기 알약이 함께 떠 있으면 그만큼 위로 올린다(서로 가리지 않게). */
  lifted = false,
}: {
  project: Project;
  lifted?: boolean;
}) {
  const { executions, retryMerge } = useAgentDockStore();
  const [open, setOpen] = useState(false);

  const pending = executions.filter(
    (execution) => execution.projectId === project.id && execution.mergeStatus === 'MANUAL',
  );
  if (pending.length === 0) return null;

  return (
    <div className={`${waiting.dock} ${lifted ? waiting.dockLifted : ''}`} data-merge-attention>
      <section className={waiting.banner} aria-label="수동 병합 필요">
        <button
          type="button"
          className={waiting.head}
          onClick={() => setOpen((prev) => !prev)}
          aria-expanded={open}
          title={open ? '접기' : '펼치기'}
        >
          <span className={waiting.headTitle}>
            <span aria-hidden="true">⚠</span> 수동 병합 필요 {pending.length}건
          </span>
          <span className={waiting.headChevron} aria-hidden="true">
            {open ? <ChevronUpIcon /> : <ChevronDownIcon />}
          </span>
        </button>
        {open && (
          <ul className={waiting.list}>
            {pending.map((execution) => (
              <li key={execution.id} className={waiting.mergeRow}>
                <span className={waiting.mergeText}>
                  #{execution.id}{' '}
                  {execution.decision?.action === 'done' ? execution.decision.summary : execution.prompt}
                  {execution.mergeDetail ? ` — ${execution.mergeDetail}` : ''}
                </span>
                <button
                  type="button"
                  className={waiting.mergeButton}
                  onClick={() => retryMerge(execution.id)}
                  title="변경을 정리한 뒤 누르면 지금 병합합니다"
                >
                  지금 병합
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
