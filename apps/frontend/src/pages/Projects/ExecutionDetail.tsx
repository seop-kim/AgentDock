import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { formatCost, formatDuration, formatTokens } from '../../lib/executions';
import exec from '../../styles/execution.module.css';
import type { Execution } from '../../types';

/** 실행이 남긴 요약 문장(Handoff 요약, 없으면 완료 계약의 요약, 질문이면 질문). */
export function executionSummary(execution: Execution): string | null {
  if (execution.handoff?.summary) return execution.handoff.summary;
  if (execution.decision?.action === 'done') return execution.decision.summary;
  if (execution.decision?.action === 'ask') return execution.decision.question;
  return null;
}

/** 실행이 바꾼 파일 목록. Handoff 가 있으면 그 목록, 없으면 트리 커밋이 남긴 git 변경 코드를 쓴다. */
function changedFileLines(execution: Execution): string[] {
  if (execution.handoff !== null) return execution.handoff.changedFiles;
  return execution.changedFiles.map((file) => `${file.status} ${file.path}`);
}

/**
 * 실행 하나를 **사람이 읽는 모양**으로 보여 준다: 마크다운 요약 + 키/값 블록(요약·변경 파일·토큰·비용·시간·세션)
 * + 접어 둔 원문(JSON). 채팅 실행 요약 카드와 실행 트리 창의 각 행이 함께 쓴다.
 * 원문은 `<details>` 로 기본이 접힘이라 화면에는 사람이 읽는 값만 남는다.
 */
export default function ExecutionDetail({ execution }: { execution: Execution }) {
  const summary = executionSummary(execution);
  const files = changedFileLines(execution);
  const metrics = execution.metrics;
  const entries: { key: string; value: string }[] = [
    { key: '변경 파일', value: files.length > 0 ? files.join('\n') : '-' },
    {
      key: '토큰',
      value: metrics
        ? `입력 ${formatTokens(metrics.inputTokens)} · 출력 ${formatTokens(metrics.outputTokens)}`
        : '-',
    },
    { key: '비용', value: metrics ? formatCost(metrics.costUsd) : '-' },
    { key: '시간', value: metrics ? formatDuration(metrics.durationMs) : '-' },
    { key: '세션', value: execution.sessionId ? execution.sessionId.slice(0, 8) : '-' },
  ];

  return (
    <div className={exec.detail}>
      {summary && (
        <div className={exec.mdBlock}>
          <span className={exec.kvKey}>요약</span>
          <div className={exec.md}>
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{summary}</ReactMarkdown>
          </div>
        </div>
      )}
      <dl className={exec.kv}>
        {entries.map((entry) => (
          <div key={entry.key} className={exec.kvRow}>
            <dt className={exec.kvKey}>{entry.key}</dt>
            <dd className={exec.kvValue}>{entry.value}</dd>
          </div>
        ))}
      </dl>
      <details className={exec.raw}>
        <summary className={exec.rawToggle}>원문 보기</summary>
        <pre className={exec.rawBody}>{JSON.stringify(execution, null, 2)}</pre>
      </details>
    </div>
  );
}
