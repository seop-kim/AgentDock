import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { api } from '../../lib/api';
import {
  MERGE_LABEL,
  MERGE_TONE,
  STATUS_LABEL,
  STATUS_TONE,
  formatCost,
  formatDuration,
  formatTokens,
  isLive,
  summarize,
  treeOrder,
} from '../../lib/executions';
import { useAgentDockStore } from '../../store/AgentDockStore';
import exec from '../../styles/execution.module.css';
import modal from '../../styles/modal.module.css';
import shared from '../../styles/shared.module.css';
import type { Execution, ExecutionStatus } from '../../types';
import { askConfirm } from '../../components/ConfirmDialog';
import ExecutionDetail from './ExecutionDetail';

/** 실행 상태 배지. 채팅 카드와 실행 트리 창이 함께 쓴다. */
export function ExecutionBadge({ status }: { status: ExecutionStatus }) {
  return <span className={`${exec.badge} ${exec[STATUS_TONE[status]]}`}>{STATUS_LABEL[status]}</span>;
}

const NOTICE_MS = 2500;

/** 취소 실패 안내를 잠깐 보여 주는 상태(ProjectDetail 의 notice 패턴과 같다). */
export function useCancelNotice(): [string | null, (message: string | null) => void] {
  const [notice, setNotice] = useState<string | null>(null);
  useEffect(() => {
    if (notice === null) return;
    const timer = window.setTimeout(() => setNotice(null), NOTICE_MS);
    return () => window.clearTimeout(timer);
  }, [notice]);
  return [notice, setNotice];
}

/**
 * 실행 취소 버튼. `isLive`(대기·실행 중·하위 대기·입력 대기) 4종에만 보인다.
 * 누르면 확인 → `취소 중…` + disabled → 취소 요청 → **성공/실패 모두** `reload()` 로 실제 상태를 반영한다.
 * 취소는 전파하지 않으므로 **이 실행 하나만** 멈춘다.
 */
export function CancelButton({
  execution,
  name,
  onError,
}: {
  execution: Execution;
  name: string;
  /** 네트워크/5xx 오류 안내(404 는 `api.cancelExecution` 이 오류로 만들지 않는다). */
  onError?: (message: string) => void;
}) {
  const { reload } = useAgentDockStore();
  const [pending, setPending] = useState(false);
  if (!isLive(execution.status)) return null;

  const cancel = async () => {
    const ok = await askConfirm(
      `"${name}"의 실행을 취소할까요?\n지금 도는 이 실행만 멈춥니다. 하위·다른 실행은 계속 돕니다.`,
      '취소',
    );
    if (!ok) return;
    setPending(true);
    try {
      await api.cancelExecution(execution.id);
    } catch (ex) {
      onError?.(`실행을 취소하지 못했습니다: ${String(ex)}`);
    } finally {
      await reload();
      setPending(false);
    }
  };

  return (
    <button type="button" className={exec.cardButton} onClick={cancel} disabled={pending}>
      {pending ? '취소 중…' : '취소'}
    </button>
  );
}

/**
 * 트리 결과 한 줄: 병합 상태 배지(병합됨/수동 병합 필요) · 커밋 sha · 변경 파일(`A path` …).
 * 트리 결과는 루트 실행에만 채워지므로 자식(mergeStatus null)에는 아무것도 그리지 않는다.
 * 수동 병합 필요(MANUAL)면 **다시 병합** 버튼을 함께 둔다(사람이 변경을 정리한 뒤 누른다).
 */
function TreeResultLine({ execution }: { execution: Execution }) {
  const { retryMerge } = useAgentDockStore();
  const status = execution.mergeStatus;
  if (status !== 'MERGED' && status !== 'MANUAL') return null;
  return (
    <p className={exec.rowFiles} title={execution.mergeDetail ?? undefined}>
      <span className={`${exec.badge} ${exec[MERGE_TONE[status]]}`}>{MERGE_LABEL[status]}</span>
      {execution.resultCommit && <> · 커밋 {execution.resultCommit}</>}
      {execution.changedFiles.length > 0 && (
        <> · {execution.changedFiles.map((file) => `${file.status} ${file.path}`).join(', ')}</>
      )}
      {status === 'MANUAL' && (
        <button type="button" className={exec.cardButton} onClick={() => retryMerge(execution.id)}>
          다시 병합
        </button>
      )}
    </p>
  );
}

/** 아직 계약을 내지 않은 실행을 "직접 처리"로 잘못 보여주지 않도록 따로 표시한다. */
function actionLabel(execution: Execution, nameOf: (id: number) => string): string {
  if (execution.decision === null) return '판단 중…';
  switch (execution.decision.action) {
    case 'delegate':
      return `→ 위임 ${nameOf(execution.decision.targetAgentId)}`;
    case 'ask':
      return '질문';
    default:
      return '직접 처리';
  }
}

/**
 * 사람에게 물은 질문(`ask` 계약)과 답 입력. 실행이 `WAITING_INPUT` 인 동안만 그린다.
 * 답을 보내면 서버가 **같은 실행**을 이어서 돌리고, 그 결과가 다시 실행 트리로 돌아온다.
 */
export function WaitingInputCard({ execution }: { execution: Execution }) {
  const { answerExecution } = useAgentDockStore();
  const [text, setText] = useState('');
  if (execution.status !== 'WAITING_INPUT') return null;

  return (
    <div className={exec.question}>
      <p className={exec.questionText}>⎿ 질문: {execution.question ?? execution.prompt}</p>
      <form
        className={exec.answer}
        onSubmit={(e) => {
          e.preventDefault();
          const answer = text.trim();
          if (answer === '') return;
          answerExecution(execution.id, answer);
          setText('');
        }}
      >
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="답을 입력하고 Enter"
          aria-label="답변 입력"
        />
        <button type="submit" disabled={text.trim() === ''}>
          답 보내기
        </button>
      </form>
    </div>
  );
}

/**
 * 채팅 응답에 붙는 실행 요약. 실행이 도는 동안에도 상태가 계속 바뀌므로,
 * "누가 누구에게 넘겼는지"를 채팅 기록 안에서 바로 볼 수 있다.
 */
export function ExecutionSummaryCard({
  executions,
  rootExecutionId,
  onOpen,
}: {
  executions: Execution[];
  rootExecutionId: number;
  onOpen: () => void;
}) {
  const { agents } = useAgentDockStore();
  const [notice, setNotice] = useCancelNotice();
  const rows = treeOrder(executions, rootExecutionId);
  if (rows.length === 0) return null;

  const total = summarize(rows.map((row) => row.execution));
  const nameOf = (id: number) => agents.find((a) => a.id === id)?.name ?? '삭제된 에이전트';

  return (
    <div className={exec.card}>
      <div className={exec.cardHead}>
        <ExecutionBadge status={rows[0].execution.status} />
        <span>
          실행 {total.executions}회 · 위임 {total.delegations}건
        </span>
        <span>
          {total.durationMs > 0
            ? `${formatCost(total.costUsd)} · ${formatDuration(total.durationMs)}`
            : '집계 중'}
        </span>
        <button type="button" className={exec.cardButton} onClick={onOpen}>
          실행 트리 보기
        </button>
      </div>
      <ul className={exec.steps}>
        {rows.map(({ execution, depth }) => (
          <li key={execution.id} className={exec.step} style={{ paddingLeft: `${depth * 12}px` }}>
            <ExecutionBadge status={execution.status} />
            <span className={exec.stepName}>{nameOf(execution.agentId)}</span>
            <span className={exec.stepAction}>{actionLabel(execution, nameOf)}</span>
            <CancelButton execution={execution} name={nameOf(execution.agentId)} onError={setNotice} />
          </li>
        ))}
      </ul>
      {notice && <p className="errorText">{notice}</p>}
      <TreeResultLine execution={rows[0].execution} />
      {/* 실행 결과는 사람이 읽는 모양(마크다운 요약 + 키/값 블록)으로 두고 원문 JSON 은 접어 둔다. */}
      <ExecutionDetail execution={rows[0].execution} />
      <span className={exec.note}>지표는 CLI 가 돌려준 실제 값입니다.</span>
    </div>
  );
}

/** 실행 트리 창. 위임이 몇 단계로 일어났고 각 실행이 얼마를 썼는지 한눈에 본다. */
export default function ExecutionTreeModal({
  executions,
  rootExecutionId,
  masterAgentId,
  onClose,
}: {
  executions: Execution[];
  rootExecutionId: number;
  masterAgentId: number | null;
  onClose: () => void;
}) {
  const { agents, removeWorktree } = useAgentDockStore();
  const [notice, setNotice] = useCancelNotice();
  const rows = treeOrder(executions, rootExecutionId);
  if (rows.length === 0) return null;

  const total = summarize(rows.map((row) => row.execution));
  const nameOf = (id: number) => agents.find((a) => a.id === id)?.name ?? '삭제된 에이전트';

  /**
   * 워크트리 정리(사람이 판단해 부른다). 한 트리 = 한 워크트리라서 루트 행에서만 정리할 수 있다.
   * 자동 병합·자동 삭제는 없으므로 결과를 확인한 뒤 사람이 지운다.
   */
  const cleanupWorktree = async (execution: Execution) => {
    const ok = await askConfirm(
      `워크트리와 전용 브랜치를 지울까요?\n${execution.worktreePath ?? ''}`,
      '정리',
    );
    if (!ok) return;
    removeWorktree(execution.id, true);
  };

  return createPortal(
    <div className={modal.overlay} onClick={onClose}>
      <div className={exec.modal} role="dialog" aria-label="실행 트리" onClick={(e) => e.stopPropagation()}>
        <div>
          <h2>실행 트리</h2>
          <p className={exec.prompt}>{rows[0].execution.prompt}</p>
        </div>

        <div className={exec.totals}>
          <span>
            실행 <strong>{total.executions}</strong>회
          </span>
          <span>
            위임 <strong>{total.delegations}</strong>건
          </span>
          <span>
            입력 <strong>{formatTokens(total.inputTokens)}</strong>
          </span>
          <span>
            출력 <strong>{formatTokens(total.outputTokens)}</strong>
          </span>
          <span>
            비용 <strong>{formatCost(total.costUsd)}</strong>
          </span>
          <span>
            소요 <strong>{formatDuration(total.durationMs)}</strong>
          </span>
        </div>

        <ol className={exec.tree}>
          {rows.map(({ execution, depth }) => (
            <li key={execution.id} className={exec.row} style={{ marginLeft: `${depth * 18}px` }}>
              <div className={exec.rowHead}>
                <ExecutionBadge status={execution.status} />
                <strong className={exec.rowName}>{nameOf(execution.agentId)}</strong>
                {execution.agentId === masterAgentId && <span className={exec.tag}>마스터</span>}
                <span className={exec.rowAction}>{actionLabel(execution, nameOf)}</span>
                <span className={exec.rowMetrics}>
                  {execution.metrics
                    ? `입력 ${formatTokens(execution.metrics.inputTokens)} · 출력 ${formatTokens(
                        execution.metrics.outputTokens,
                      )} · ${formatCost(execution.metrics.costUsd)} · ${formatDuration(
                        execution.metrics.durationMs,
                      )} · 세션 ${execution.sessionId.slice(0, 8)}`
                    : '실행 중…'}
                </span>
                <CancelButton execution={execution} name={nameOf(execution.agentId)} onError={setNotice} />
              </div>
              {execution.worktreeBranch && (
                <p className={exec.rowFiles} title={execution.worktreePath ?? undefined}>
                  워크트리 {execution.worktreePath} · {execution.worktreeBranch}
                  {execution.parentExecutionId === null && (
                    <button type="button" className={exec.cardButton} onClick={() => cleanupWorktree(execution)}>
                      워크트리 정리
                    </button>
                  )}
                </p>
              )}
              {execution.parentExecutionId === null && <TreeResultLine execution={execution} />}
              {/* 결과·Handoff 를 원문 텍스트로 쏟지 않고 사람이 읽는 모양으로 그린다(원문 JSON 은 접어 둔다). */}
              <ExecutionDetail execution={execution} />
            </li>
          ))}
        </ol>

        <p className={shared.muted}>
          상태 전이는 실제 순서(판단 → 위임 → 하위 실행 → 취합)를 그대로 따르지만, 화면에서는 빠르게 재생합니다. 지표는 모의
          값이고, 실제 구현에서는 CLI 의 <code>--output-format json</code> 이 돌려주는 usage / total_cost_usd / duration_ms 를
          씁니다. 판단 실행(마스터·리더)은 한 실행 안에서 판단과 취합 두 스텝을 돌기 때문에 호출량이 두 배로 잡힙니다.
        </p>

        {notice && <p className="errorText">{notice}</p>}

        <div className={modal.actions}>
          <button type="button" onClick={onClose}>
            닫기
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
