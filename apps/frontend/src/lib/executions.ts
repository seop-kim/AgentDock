import type { ExecutionTree } from './api';

const STATUS_LABELS: Record<string, string> = {
  PENDING: '대기',
  RUNNING: '실행 중',
  WAITING_CHILD: '하위 대기',
  SUCCEEDED: '완료',
  FAILED: '실패',
  CANCELLED: '취소',
};

export function statusLabel(status: string | null | undefined): string {
  if (!status) return '대기';
  return STATUS_LABELS[status] ?? status;
}

/** 배지 색: 실행 중 / 대기 / 완료 / 실패 (styles/execution.module.css 의 클래스 이름과 맞춘다). */
export function statusClass(status: string | null | undefined): 'active' | 'wait' | 'done' | 'failed' {
  switch (status) {
    case 'RUNNING':
      return 'active';
    case 'PENDING':
    case 'WAITING_CHILD':
      return 'wait';
    case 'FAILED':
    case 'CANCELLED':
      return 'failed';
    default:
      return 'done';
  }
}

export function isRunning(status: string | null | undefined): boolean {
  return status === 'PENDING' || status === 'RUNNING' || status === 'WAITING_CHILD';
}

/** 트리에 아직 돌고 있는 실행이 있으면 참(자동 갱신 여부 판단). */
export function treeIsRunning(tree: ExecutionTree | null): boolean {
  return tree !== null && tree.nodes.some((node) => isRunning(node.execution.status));
}

export function formatCost(value: number | null | undefined): string {
  return value === null || value === undefined ? '-' : `$${value.toFixed(4)}`;
}

export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return '-';
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`;
}

export function formatTokens(value: number | null | undefined): string {
  return value === null || value === undefined ? '-' : value.toLocaleString('ko-KR');
}

/** 트리 전체 합계(실행 수 · 위임 수 · 비용 · 토큰). 위임 수는 마지막 계약이 마무리여도 남아 있는 위임 대상으로 센다. */
export function treeTotals(tree: ExecutionTree) {
  const delegations = tree.nodes.filter(
    (node) => node.execution.delegatedTargetAgentId !== null && node.execution.delegatedTargetAgentId !== undefined,
  ).length;
  const cost = tree.nodes.reduce((sum, node) => sum + (node.execution.costUsd ?? 0), 0);
  const tokens = tree.nodes.reduce(
    (sum, node) => sum + (node.execution.inputTokens ?? 0) + (node.execution.outputTokens ?? 0),
    0,
  );
  return { executions: tree.nodes.length, delegations, cost, tokens };
}

/** 실행이 낸 요약(Handoff 의 summary, 없으면 결과 텍스트에서 JSON 을 풀어서). */
export function summaryOf(resultText: string | null | undefined): string {
  if (!resultText) return '';
  const start = resultText.indexOf('{');
  const end = resultText.lastIndexOf('}');
  if (start >= 0 && end > start) {
    try {
      const parsed = JSON.parse(resultText.slice(start, end + 1)) as { summary?: unknown; action?: unknown };
      if (typeof parsed.summary === 'string') return parsed.summary;
    } catch {
      // JSON 이 아니면 원문을 그대로 보여 준다.
    }
  }
  return resultText;
}

export function changedFilesOf(resultText: string | null | undefined): string[] {
  if (!resultText) return [];
  const start = resultText.indexOf('{');
  const end = resultText.lastIndexOf('}');
  if (start < 0 || end <= start) return [];
  try {
    const parsed = JSON.parse(resultText.slice(start, end + 1)) as { changedFiles?: unknown };
    return Array.isArray(parsed.changedFiles) ? parsed.changedFiles.map(String) : [];
  } catch {
    return [];
  }
}
