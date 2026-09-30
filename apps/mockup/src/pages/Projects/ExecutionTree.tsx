import { createPortal } from 'react-dom';
import { STATUS_LABEL, STATUS_TONE, formatCost, formatDuration, formatTokens, summarize, treeOrder } from '../../lib/executions';
import { useMockStore } from '../../store/MockStore';
import exec from '../../styles/execution.module.css';
import modal from '../../styles/modal.module.css';
import shared from '../../styles/shared.module.css';
import type { Execution, ExecutionStatus } from '../../types';

/** 실행 상태 배지. 채팅 카드와 실행 트리 창이 함께 쓴다. */
export function ExecutionBadge({ status }: { status: ExecutionStatus }) {
  return <span className={`${exec.badge} ${exec[STATUS_TONE[status]]}`}>{STATUS_LABEL[status]}</span>;
}

/** 아직 계약을 내지 않은 실행을 "직접 처리"로 잘못 보여주지 않도록 따로 표시한다. */
function actionLabel(execution: Execution, nameOf: (id: number) => string): string {
  if (execution.decision === null) return '판단 중…';
  return execution.decision.action === 'delegate'
    ? `→ 위임 ${nameOf(execution.decision.targetAgentId)}`
    : '직접 처리';
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
  const { agents } = useMockStore();
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
          </li>
        ))}
      </ul>
      <span className={exec.note}>지표는 모의 값입니다.</span>
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
  const { agents } = useMockStore();
  const rows = treeOrder(executions, rootExecutionId);
  if (rows.length === 0) return null;

  const total = summarize(rows.map((row) => row.execution));
  const nameOf = (id: number) => agents.find((a) => a.id === id)?.name ?? '삭제된 에이전트';

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
              </div>
              {execution.handoff && <p className={exec.rowSummary}>{execution.handoff.summary}</p>}
              {execution.handoff !== null && execution.handoff.changedFiles.length > 0 && (
                <p className={exec.rowFiles}>{execution.handoff.changedFiles.join('  ')}</p>
              )}
            </li>
          ))}
        </ol>

        <p className={shared.muted}>
          상태 전이는 실제 순서(판단 → 위임 → 하위 실행 → 취합)를 그대로 따르지만, 화면에서는 빠르게 재생합니다. 지표는 모의
          값이고, 실제 구현에서는 CLI 의 <code>--output-format json</code> 이 돌려주는 usage / total_cost_usd / duration_ms 를
          씁니다. 판단 실행(마스터·리더)은 한 실행 안에서 판단과 취합 두 스텝을 돌기 때문에 호출량이 두 배로 잡힙니다.
        </p>

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
