import { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { api, ExecutionTree } from '../lib/api';
import {
  changedFilesOf,
  formatCost,
  formatDuration,
  formatTokens,
  statusClass,
  statusLabel,
  summaryOf,
  treeIsRunning,
  treeTotals,
} from '../lib/executions';
import executionStyles from '../styles/execution.module.css';
import modalStyles from '../styles/modal.module.css';

interface Props {
  executionId: number;
  onClose: () => void;
}

const BADGE_CLASS: Record<string, string> = {
  active: executionStyles.active,
  wait: executionStyles.wait,
  done: executionStyles.done,
  failed: executionStyles.failed,
};

/** 실행 트리 창(모달). 돌고 있는 실행이 있으면 2초마다 갱신한다. */
export default function ExecutionTreeModal({ executionId, onClose }: Props) {
  const [tree, setTree] = useState<ExecutionTree | null>(null);
  const [error, setError] = useState<string | null>(null);
  const runningRef = useRef(false);

  const load = useCallback(async () => {
    try {
      setTree(await api.getExecutionTree(executionId));
      setError(null);
    } catch (ex) {
      setError(String(ex));
    }
  }, [executionId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    runningRef.current = treeIsRunning(tree);
  }, [tree]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (runningRef.current) void load();
    }, 2000);
    return () => window.clearInterval(timer);
  }, [load]);

  const totals = tree === null ? null : treeTotals(tree);

  return createPortal(
    <div className={modalStyles.overlay} onClick={onClose}>
      <div className={executionStyles.modal} onClick={(event) => event.stopPropagation()}>
        <h2>실행 트리</h2>
        <p className={executionStyles.prompt}>
          명령 하나가 실행 트리 하나입니다. 판단한 실행은 계약(위임/마무리)을 남기고, 위임한 대상이 함께 남습니다.
        </p>
        {error !== null && <div className="errorText">{error}</div>}
        {totals !== null && tree !== null && (
          <>
            <div className={executionStyles.totals}>
              <span>실행 {totals.executions}건</span>
              <span>위임 {totals.delegations}건</span>
              <span>비용 {formatCost(totals.cost)}</span>
              <span>토큰 {formatTokens(totals.tokens)}</span>
            </div>
            <ul className={executionStyles.tree}>
              {tree.nodes.map((node) => {
                const execution = node.execution;
                const summary = summaryOf(execution.resultText);
                const files = changedFilesOf(execution.resultText);
                return (
                  <li
                    key={execution.id}
                    className={executionStyles.row}
                    style={{ marginLeft: node.depth * 22 }}
                  >
                    <div className={executionStyles.rowHead}>
                      <span className={`${executionStyles.badge} ${BADGE_CLASS[statusClass(execution.status)]}`}>
                        {statusLabel(execution.status)}
                      </span>
                      <strong className={executionStyles.rowName}>
                        {node.depth > 0 && '↳ '}
                        {node.agentName ?? `에이전트 ${execution.agentId ?? '?'}`}
                      </strong>
                      {/* 위임한 실행은 마지막 계약이 마무리여도 위임 대상이 남는다(누구에게 맡겼는지 화면에 남긴다). */}
                      {(execution.delegatedTargetAgentId !== null &&
                        execution.delegatedTargetAgentId !== undefined) && (
                        <span className={executionStyles.tag}>
                          위임 → {node.targetAgentName ?? `에이전트 ${execution.delegatedTargetAgentId}`}
                        </span>
                      )}
                      {execution.decision === 'DONE' && <span className={executionStyles.rowAction}>마무리</span>}
                      <span className={executionStyles.rowMetrics}>
                        {formatCost(execution.costUsd)} · {formatDuration(execution.durationMs)} · 토큰{' '}
                        {formatTokens((execution.inputTokens ?? 0) + (execution.outputTokens ?? 0))} · 로그{' '}
                        {node.logCount}
                      </span>
                      <button
                        type="button"
                        className={executionStyles.cardButton}
                        onClick={() =>
                          window.open(`/terminal/${execution.id}`, '_blank', 'width=980,height=680,noopener')
                        }
                      >
                        터미널
                      </button>
                    </div>
                    {summary !== '' && <p className={executionStyles.rowSummary}>{summary}</p>}
                    {execution.errorMessage !== null && execution.errorMessage !== undefined && (
                      <p className={executionStyles.rowFiles}>{execution.errorMessage}</p>
                    )}
                    {files.length > 0 && (
                      <p className={executionStyles.rowFiles}>변경 파일: {files.join(', ')}</p>
                    )}
                  </li>
                );
              })}
            </ul>
          </>
        )}
        <div className={modalStyles.actions}>
          <button type="button" onClick={onClose}>
            닫기
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
