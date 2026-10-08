import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { questionText, waitingInputs } from '../../lib/executions';
import { api } from '../../lib/api';
import { useAgentDockStore } from '../../store/AgentDockStore';
import type { Execution, Project } from '../../types';
import panel from './RequestsPanel.module.css';

/** "무시" 한 트리(실행 id)를 담아 두는 열쇠. 알림만 끄는 것이라 서버 상태는 건드리지 않는다. */
const DISMISSED_KEY = 'agentdock.dismissedMerges';

/** 닫힘 애니메이션이 끝나기를 기다리는 시간(ms). CSS 의 sink 길이와 맞춘다. */
const CLOSE_MS = 180;

function readDismissed(): number[] {
  try {
    const raw = window.localStorage.getItem(DISMISSED_KEY);
    return raw === null ? [] : (JSON.parse(raw) as number[]);
  } catch {
    return [];
  }
}

/**
 * 사람에게 **요청하는 것**을 한 자리에 모은다.
 *  - 입력 대기: 에이전트가 계약 `ask` 로 답을 기다리는 실행
 *  - 수동 병합: 자동 병합이 막혀(메인 저장소에 변경이 있음) 판단이 필요한 트리
 *
 * <p>화면 하단에 붙은 긴 패널이 **아래에서 위로 올라오고**, 닫을 때는 **부드럽게 아래로 내려간 뒤** 사라진다
 * (닫힘 애니메이션이 끝날 때까지 화면에 남겨 둔다). 뒤를 흐리지 않아 캔버스가 그대로 보이고, 빈 곳을 누르면 닫힌다.
 *
 * <p>입력 대기의 보기는 **한 행씩 고르는 선택지**다 — 누른 즉시 보내지 않고 `답 보내기` 를 눌러야 전송된다.
 * 수동 병합은 `무시` 로 알림만 접을 수 있다(병합 자체는 그대로 남는다).
 */
/**
 * 같은 질문이 **부모와 자식에 함께** 걸린다(자식이 물으면 부모도 입력 대기로 전파된다).
 * 같은 질문은 하나만 남기고, 답은 트리 위쪽(부모)에 보낸다 — 그래야 그 실행이 이어서 돈다.
 */
function dedupeAsks(waits: Execution[]): Execution[] {
  const seen = new Set<string>();
  const ordered = [...waits].sort(
    (left, right) =>
      (left.parentExecutionId === null ? -1 : 0) - (right.parentExecutionId === null ? -1 : 0),
  );
  return ordered.filter((execution) => {
    const key = questionText(execution).trim();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export default function RequestsPanel({ project }: { project: Project }) {
  const { executions, agents, retryMerge, answerExecution, reload } = useAgentDockStore();
  const [open, setOpen] = useState(false);
  /** 닫히는 중(아래로 내려가는 애니메이션 동안 화면에 남긴다). */
  const [closing, setClosing] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [dismissed, setDismissed] = useState<number[]>(readDismissed);
  // 상세에서 고른 보기 / 직접 입력한 답. `답 보내기` 를 눌러야 실제로 나간다.
  const [choice, setChoice] = useState<string | null>(null);
  const [text, setText] = useState('');
  const closeTimer = useRef<number | null>(null);

  // 닫히는 도중에 컴포넌트가 사라져도 타이머가 남지 않게.
  useEffect(
    () => () => {
      if (closeTimer.current !== null) {
        window.clearTimeout(closeTimer.current);
      }
    },
    [],
  );

  const mine = executions.filter((execution) => execution.projectId === project.id);
  const waits = dedupeAsks(waitingInputs(mine));
  const merges = mine.filter(
    (execution) => execution.mergeStatus === 'MANUAL' && !dismissed.includes(execution.id),
  );
  const items: { key: string; kind: 'ask' | 'merge'; execution: Execution }[] = [
    ...waits.map((execution) => ({ key: `ask-${execution.id}`, kind: 'ask' as const, execution })),
    ...merges.map((execution) => ({ key: `merge-${execution.id}`, kind: 'merge' as const, execution })),
  ];
  if (items.length === 0) return null;

  const nameOf = (agentId: number) => agents.find((agent) => agent.id === agentId)?.name ?? '에이전트';
  const current = items.find((item) => item.key === selected) ?? items[0];

  const openPanel = () => {
    if (closeTimer.current !== null) {
      window.clearTimeout(closeTimer.current);
      closeTimer.current = null;
    }
    setClosing(false);
    setOpen(true);
  };

  /** 닫기: 아래로 내려가는 애니메이션이 끝난 뒤 실제로 감춘다. */
  const close = () => {
    if (closeTimer.current !== null) {
      window.clearTimeout(closeTimer.current);
    }
    setClosing(true);
    closeTimer.current = window.setTimeout(() => {
      closeTimer.current = null;
      setOpen(false);
      setClosing(false);
    }, CLOSE_MS);
  };

  const pick = (key: string) => {
    setSelected(key);
    setChoice(null);
    setText('');
  };

  const sendAnswer = (execution: Execution) => {
    const value = (choice ?? text).trim();
    if (value === '') return;
    answerExecution(execution.id, value);
    setChoice(null);
    setText('');
    setSelected(null);
  };

  /** 답하는 대신 그 실행을 멈춘다(위임된 자식도 함께 멈춘다). */
  const stopExecution = (execution: Execution) => {
    const ok = window.confirm(
      `실행 #${execution.id} 을(를) 중지할까요? 위임된 자식 실행도 함께 멈춥니다.`,
    );
    if (!ok) return;
    void api.cancelExecution(execution.id).then(() => reload());
    setSelected(null);
  };

  const dismiss = (executionId: number) => {
    const next = [...dismissed, executionId];
    setDismissed(next);
    try {
      window.localStorage.setItem(DISMISSED_KEY, JSON.stringify(next));
    } catch {
      // 저장하지 못해도 이번 화면에서는 접힌다.
    }
    setSelected(null);
  };

  const canSend = (choice ?? text).trim() !== '';

  return (
    <>
      <div className={panel.dock}>
        <button
          type="button"
          className={panel.pill}
          onClick={openPanel}
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
          // 뒤를 흐리지 않는다 — 빈 곳을 누르면 닫히도록 투명한 판만 깐다.
          <div className={panel.backdrop} onClick={close}>
            <section
              className={`${panel.sheet} ${closing ? panel.sheetClosing : ''}`}
              role="dialog"
              aria-label="내가 처리할 요청"
              onClick={(e) => e.stopPropagation()}
            >
              <header className={panel.head}>
                <strong>내가 처리할 요청 {items.length}건</strong>
                <button type="button" className={panel.close} onClick={close}>
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
                        onClick={() => pick(item.key)}
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
                    <>
                      <p className={panel.detailTitle}>{nameOf(current.execution.agentId)} 가 묻습니다</p>
                      <p className={panel.detailText}>{questionText(current.execution)}</p>

                      {current.execution.options.length > 0 && (
                        <ul className={panel.options}>
                          {current.execution.options.map((option) => (
                            <li key={option}>
                              <button
                                type="button"
                                className={`${panel.optionRow} ${choice === option ? panel.optionRowOn : ''}`}
                                aria-pressed={choice === option}
                                onClick={() => {
                                  setChoice(option);
                                  setText('');
                                }}
                              >
                                <span className={panel.optionMark} aria-hidden="true">
                                  {choice === option ? '●' : '○'}
                                </span>
                                {option}
                              </button>
                            </li>
                          ))}
                        </ul>
                      )}

                      <form
                        className={panel.answerRow}
                        onSubmit={(e) => {
                          e.preventDefault();
                          sendAnswer(current.execution);
                        }}
                      >
                        <input
                          value={text}
                          onChange={(e) => {
                            setText(e.target.value);
                            setChoice(null);
                          }}
                          placeholder={
                            current.execution.options.length > 0 ? '또는 직접 입력' : '답을 입력하세요'
                          }
                          aria-label="답변 입력"
                        />
                        <button type="submit" className={panel.sendButton} disabled={!canSend}>
                          답 보내기
                        </button>
                      </form>
                      <p className={panel.hint}>
                        {canSend
                          ? `보낼 답: ${(choice ?? text).trim()}`
                          : '보기를 고르거나 답을 입력한 뒤 답 보내기를 누르세요.'}
                      </p>
                      {/* 답하기 어려운 질문이면 답하는 대신 그 실행을 멈출 수 있게 한다. */}
                      <div className={panel.actions}>
                        <button
                          type="button"
                          className={panel.dismiss}
                          onClick={() => stopExecution(current.execution)}
                          title="이 실행을 중지합니다(위임된 자식도 함께)"
                        >
                          에이전트 중지
                        </button>
                      </div>
                    </>
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
                      <div className={panel.actions}>
                        <button
                          type="button"
                          className={panel.dismiss}
                          onClick={() => dismiss(current.execution.id)}
                          title="이 알림만 접습니다(브랜치와 병합 상태는 그대로 남습니다)"
                        >
                          무시
                        </button>
                        <button
                          type="button"
                          className={panel.mergeButton}
                          onClick={() => retryMerge(current.execution.id)}
                        >
                          지금 병합
                        </button>
                      </div>
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
