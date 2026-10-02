import { useState } from 'react';
import { createPortal } from 'react-dom';
import { questionText, waitingInputs } from '../../lib/executions';
import { useAgentDockStore } from '../../store/AgentDockStore';
import modal from '../../styles/modal.module.css';
import type { Execution, Project } from '../../types';
import panel from './RequestsPanel.module.css';
import { WaitingInputItem } from './WaitingInputPopup';

/**
 * 사람에게 **요청하는 것**을 한 자리에 모은다.
 *  - 입력 대기: 에이전트가 계약 `ask` 로 답을 기다리는 실행
 *  - 수동 병합: 자동 병합이 막혀(메인 저장소에 변경이 있음) 판단이 필요한 트리
 *
 * <p>화면 아래 가운데 알약을 누르면 **아래에서 올라오는 창**이 열리고, 왼쪽 목록에서 고른 요청의
 * 상세를 오른쪽에서 바로 처리한다(답 보내기 / 지금 병합). 둘 다 "사람이 해 줘야 하는 일"이라
 * 자리를 나누지 않고 한 창에 모았다.
 */
export default function RequestsPanel({ project }: { project: Project }) {
  const { executions, agents, retryMerge } = useAgentDockStore();
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);

  const mine = executions.filter((execution) => execution.projectId === project.id);
  const waits = waitingInputs(mine);
  const merges = mine.filter((execution) => execution.mergeStatus === 'MANUAL');
  const items: { key: string; kind: 'ask' | 'merge'; execution: Execution }[] = [
    ...waits.map((execution) => ({ key: `ask-${execution.id}`, kind: 'ask' as const, execution })),
    ...merges.map((execution) => ({ key: `merge-${execution.id}`, kind: 'merge' as const, execution })),
  ];
  if (items.length === 0) return null;

  const nameOf = (agentId: number) => agents.find((agent) => agent.id === agentId)?.name ?? '에이전트';
  const current = items.find((item) => item.key === selected) ?? items[0];

  return (
    <>
      <div className={panel.dock}>
        <button
          type="button"
          className={panel.pill}
          onClick={() => setOpen(true)}
          aria-label="내가 처리할 요청 열기"
        >
          <span aria-hidden="true">💬</span> 내가 처리할 요청 {items.length}건
          <span className={panel.pillDetail}>
            {waits.length > 0 ? ` 입력 대기 ${waits.length}` : ''}
            {merges.length > 0 ? ` 수동 병합 ${merges.length}` : ''}
          </span>
        </button>
      </div>

      {open &&
        createPortal(
          <div className={modal.overlay} onClick={() => setOpen(false)}>
            <section
              className={panel.sheet}
              role="dialog"
              aria-label="내가 처리할 요청"
              onClick={(e) => e.stopPropagation()}
            >
              <header className={panel.head}>
                <strong>내가 처리할 요청 {items.length}건</strong>
                <button type="button" className={panel.close} onClick={() => setOpen(false)}>
                  닫기
                </button>
              </header>

              <div className={panel.body}>
                <ul className={panel.list}>
                  {items.map((item) => (
                    <li key={item.key}>
                      <button
                        type="button"
                        className={`${panel.row} ${item.key === current.key ? panel.rowActive : ''}`}
                        onClick={() => setSelected(item.key)}
                      >
                        <span className={panel.rowKind}>{item.kind === 'ask' ? '입력 대기' : '수동 병합'}</span>
                        <span className={panel.rowText}>
                          {item.kind === 'ask'
                            ? `${nameOf(item.execution.agentId)}: ${questionText(item.execution)}`
                            : `#${item.execution.id} 트리 병합이 막혀 있습니다`}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>

                <div className={panel.detail}>
                  {current.kind === 'ask' ? (
                    <WaitingInputItem
                      execution={current.execution}
                      agentName={nameOf(current.execution.agentId)}
                    />
                  ) : (
                    <>
                      <p className={panel.detailTitle}>트리 #{current.execution.id} 자동 병합이 막혔습니다</p>
                      <p className={panel.detailText}>
                        {current.execution.mergeDetail ??
                          '메인 저장소에 변경이 있어 자동으로 병합하지 않았습니다. 변경을 정리한 뒤 누르세요.'}
                      </p>
                      {current.execution.decision?.action === 'done' && (
                        <p className={panel.detailText}>{current.execution.decision.summary}</p>
                      )}
                      <button
                        type="button"
                        className={panel.mergeButton}
                        onClick={() => retryMerge(current.execution.id)}
                      >
                        지금 병합
                      </button>
                    </>
                  )}
                </div>
              </div>
            </section>
          </div>,
          document.body,
        )}
    </>
  );
}
