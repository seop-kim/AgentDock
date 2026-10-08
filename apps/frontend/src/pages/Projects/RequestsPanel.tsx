import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { questionText, waitingInputs } from '../../lib/executions';
import { api, type ServerError } from '../../lib/api';
import { askConfirm } from '../../components/ConfirmDialog';
import { toast } from '../../components/Toaster';
import { useAgentDockStore } from '../../store/AgentDockStore';
import type { Execution, Project } from '../../types';
import panel from './RequestsPanel.module.css';

/** 요청 창에 뜨는 한 줄. 입력 대기·수동 병합은 실행에서, 서버 오류는 오류 기록에서 온다. */
type Item =
  | { key: string; kind: 'ask'; execution: Execution }
  | { key: string; kind: 'merge'; execution: Execution }
  | { key: string; kind: 'error'; serverError: ServerError };

/** 마스터에게 보낼 문장 — 재현 경로·원인 정리와 재발 방지까지 요구한다. */
function errorText(item: ServerError): string {
  return [
    '서버 내부 오류를 고쳐 주세요.',
    '',
    `[오류] ${item.time} ${item.method} ${item.path}`,
    `[예외] ${item.exception}: ${item.message}`,
    '[스택]',
    item.stack,
    '',
    '재현 경로와 원인을 정리하고, 재발 방지까지 포함해 수정하세요.',
  ].join('\n');
}

/** "무시" 한 트리(실행 id)를 담아 두는 열쇠. 알림만 끄는 것이라 서버 상태는 건드리지 않는다. */
const DISMISSED_KEY = 'agentdock.dismissedMerges';

/** 닫힘 애니메이션이 끝나기를 기다리는 시간(ms). CSS 의 sink 길이와 맞춘다. */
const CLOSE_MS = 180;

function readDismissed(): string[] {
  try {
    const raw = window.localStorage.getItem(DISMISSED_KEY);
    return raw === null ? [] : (JSON.parse(raw) as unknown[]).map((value) => String(value));
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
 * 보여 줄 입력 대기만 고른다.
 *  1) 같은 질문이 부모·자식에 함께 걸리므로(자식이 물으면 부모에게 전파된다) 트리 위쪽 하나만 남긴다.
 *  2) 조상이 아직 **일하는 중**이면 그 질문은 위에서 처리 중이다 — 답을 보낸 뒤에도 자식의 옛 대기 표시가
 *     남아 "입력 대기"로 계속 보이던 문제를 막는다.
 */
function visibleAsks(waits: Execution[], all: Execution[]): Execution[] {
  const byId = new Map(all.map((execution) => [execution.id, execution]));
  const busy = new Set(
    all
      .filter(
        (execution) =>
          execution.status === 'RUNNING' ||
          execution.status === 'QUEUED' ||
          execution.status === 'WAITING_CHILD',
      )
      .map((execution) => execution.id),
  );
  const ordered = [...waits].sort(
    (left, right) =>
      (left.parentExecutionId === null ? -1 : 0) - (right.parentExecutionId === null ? -1 : 0),
  );
  const seen = new Set<string>();
  return ordered.filter((execution) => {
    let cursor = execution.parentExecutionId;
    while (cursor !== null) {
      if (busy.has(cursor)) return false;
      cursor = byId.get(cursor)?.parentExecutionId ?? null;
    }
    const key = questionText(execution).trim();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export default function RequestsPanel({ project }: { project: Project }) {
  const { executions, agents, retryMerge, answerExecution, reload, sendCommand, error: requestError } =
    useAgentDockStore();
  const [open, setOpen] = useState(false);
  /** 닫히는 중(아래로 내려가는 애니메이션 동안 화면에 남긴다). */
  const [closing, setClosing] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [dismissed, setDismissed] = useState<string[]>(readDismissed);
  /** 서버 내부 오류(5xx) — 사람이 원인을 보고 에이전트에게 수정을 맡길지 고른다. */
  const [serverErrors, setServerErrors] = useState<ServerError[]>([]);
  // 상세에서 고른 보기 / 직접 입력한 답. `답 보내기` 를 눌러야 실제로 나간다.
  const [choice, setChoice] = useState<string | null>(null);
  const [text, setText] = useState('');
  const closeTimer = useRef<number | null>(null);

  // 서버 내부 오류는 20초마다, 그리고 요청이 실패할 때마다 다시 읽는다(부가 기능이라 조용히 넘긴다).
  useEffect(() => {
    let alive = true;
    const load = () => {
      void api
        .listServerErrors()
        .then((rows) => {
          if (alive) setServerErrors(rows);
        })
        .catch(() => {
          // 조회 실패는 무시한다.
        });
    };
    load();
    const timer = window.setInterval(load, 20000);
    return () => {
      alive = false;
      window.clearInterval(timer);
    };
  }, [requestError]);

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
  const waits = visibleAsks(waitingInputs(mine), mine);
  const merges = mine.filter((execution) => execution.mergeStatus === 'MANUAL');
  const errorItems: Item[] = serverErrors
    .filter((item) => !dismissed.includes(`error-${item.id}`))
    .map((item) => ({ key: `error-${item.id}`, kind: 'error' as const, serverError: item }));
  const items: Item[] = [
    ...errorItems,
    ...waits.map((execution) => ({ key: `ask-${execution.id}`, kind: 'ask' as const, execution })),
    ...merges.map((execution) => ({ key: `merge-${execution.id}`, kind: 'merge' as const, execution })),
    // 무시한 알림은 접어 둔다(브라우저에만 저장 — 서버 상태는 그대로).
  ].filter((item) => !dismissed.includes(item.key));
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
  const stopExecution = async (execution: Execution) => {
    const ok = await askConfirm(
      `실행 #${execution.id} 을(를) 중지할까요? 위임된 자식 실행도 함께 멈춥니다.`,
      '중지',
    );
    if (!ok) return;
    void api.cancelExecution(execution.id).then(() => reload());
    toast(`실행 #${execution.id} 중지를 요청했습니다`);
    setSelected(null);
  };

  /** 서버 오류를 마스터에게 맡긴다 — 기존 명령 경로를 그대로 쓰므로 트리·워크트리·병합 흐름이 다 적용된다. */
  const handOver = (serverError: ServerError) => {
    if (project.masterAgentId === null) return;
    sendCommand(project.id, { kind: 'agent', id: project.masterAgentId }, errorText(serverError), []);
    toast(`서버 오류 #${serverError.id} 수정을 마스터에게 맡겼습니다`, 'success');
    dismiss(`error-${serverError.id}`);
  };

  /** 알림 하나를 접는다(무시). 서버의 실행·병합 상태는 건드리지 않는다. */
  const dismiss = (key: string) => {
    const next = [...dismissed, key];
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
            {errorItems.length > 0 ? ` 서버 오류 ${errorItems.length}` : ''}
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
                        <span className={panel.rowKind}>
                          {item.kind === 'ask' ? '입력 대기' : item.kind === 'merge' ? '수동 병합' : '서버 오류'}
                        </span>
                        <span className={panel.rowText}>
                          {item.kind === 'ask'
                            ? `${nameOf(item.execution.agentId)}: ${questionText(item.execution)}`
                            : item.kind === 'merge'
                              ? `#${item.execution.id} 트리 병합이 막혀 있습니다`
                              : `${item.serverError.exception}: ${item.serverError.message}`}
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
                      {/* 답하기 어려운 질문이면 답하는 대신 멈추거나, 알림만 접을 수 있다. */}
                      <div className={panel.actions}>
                        <button
                          type="button"
                          className={panel.dismiss}
                          onClick={() => dismiss(current.key)}
                          title="이 알림만 접습니다(실행은 그대로 대기)"
                        >
                          무시
                        </button>
                        <button
                          type="button"
                          className={panel.dismiss}
                          onClick={() => void stopExecution(current.execution)}
                          title="이 실행을 중지합니다(위임된 자식도 함께)"
                        >
                          에이전트 중지
                        </button>
                      </div>
                    </>
                  ) : current.kind === 'merge' ? (
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
                          onClick={() => dismiss(current.key)}
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
                  ) : (
                    <>
                      <p className={panel.detailTitle}>서버 내부 오류 #{current.serverError.id}</p>
                      <p className={panel.detailText}>
                        {new Date(current.serverError.time).toLocaleString('ko-KR')} · {current.serverError.method}{' '}
                        {current.serverError.path}
                      </p>
                      <p className={panel.detailText}>
                        {current.serverError.exception}: {current.serverError.message}
                      </p>
                      <pre className={panel.stack}>{current.serverError.stack}</pre>
                      <div className={panel.actions}>
                        <button
                          type="button"
                          className={panel.dismiss}
                          onClick={() => dismiss(current.key)}
                          title="이 알림만 접습니다(서버 기록은 그대로)"
                        >
                          무시
                        </button>
                        <button
                          type="button"
                          className={panel.mergeButton}
                          disabled={project.masterAgentId === null}
                          onClick={() => handOver(current.serverError)}
                          title={
                            project.masterAgentId === null
                              ? '이 프로젝트에 마스터 에이전트가 없습니다'
                              : '마스터가 원인을 정리하고 고칩니다'
                          }
                        >
                          에이전트에게 수정 맡기기
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
