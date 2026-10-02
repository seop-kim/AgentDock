import type { Execution, ExecutionStatus, MergeStatus } from '../types';

/* 실행 트리를 화면에 그릴 때 쓰는 조회·표시 도우미. */

/** 상태 배지에 쓰는 한국어 이름. */
export const STATUS_LABEL: Record<ExecutionStatus, string> = {
  QUEUED: '대기',
  RUNNING: '실행 중',
  WAITING_CHILD: '하위 대기',
  WAITING_INPUT: '입력 대기',
  DONE: '완료',
  FAILED: '실패',
  CANCELLED: '취소',
};

/** 배지 색을 고르는 묶음. CSS 에서 이 이름으로 클래스를 만든다. */
export const STATUS_TONE: Record<ExecutionStatus, 'wait' | 'active' | 'done' | 'failed'> = {
  QUEUED: 'wait',
  RUNNING: 'active',
  WAITING_CHILD: 'wait',
  WAITING_INPUT: 'wait',
  DONE: 'done',
  FAILED: 'failed',
  /* 취소는 끝난 것이다 — 대기(노랑)로 보이면 아직 돌고 있는 것처럼 읽힌다. */
  CANCELLED: 'done',
};

/** 트리 결과 배지의 한국어 이름(실행 트리 창 루트 행·채팅 요약 카드·터미널 머리말이 함께 쓴다). */
export const MERGE_LABEL: Record<MergeStatus, string> = {
  MERGED: '병합됨',
  MANUAL: '수동 병합 필요',
  NONE: '변경 없음',
};

/** 트리 결과 배지 색. 기존 배지 색 클래스(active/wait/done)를 그대로 쓴다. */
export const MERGE_TONE: Record<MergeStatus, 'active' | 'wait' | 'done'> = {
  MERGED: 'active',
  MANUAL: 'wait',
  NONE: 'done',
};

export interface ExecutionRow {
  execution: Execution;
  /** 트리에서의 깊이. 들여쓰기에 쓴다. */
  depth: number;
}

/** 루트 실행과 그 자손을 트리 순서(부모 → 자식)로 편다. */
export function treeOrder(executions: Execution[], rootId: number): ExecutionRow[] {
  const byParent = new Map<number, Execution[]>();
  executions.forEach((execution) => {
    if (execution.parentExecutionId === null) return;
    const siblings = byParent.get(execution.parentExecutionId) ?? [];
    byParent.set(execution.parentExecutionId, [...siblings, execution]);
  });

  const root = executions.find((execution) => execution.id === rootId);
  if (!root) return [];

  const rows: ExecutionRow[] = [{ execution: root, depth: 0 }];
  const walk = (parentId: number, depth: number) => {
    (byParent.get(parentId) ?? []).forEach((child) => {
      rows.push({ execution: child, depth });
      walk(child.id, depth + 1);
    });
  };
  walk(rootId, 1);
  return rows;
}

export interface ExecutionTotals {
  executions: number;
  delegations: number;
  costUsd: number;
  /** 루트 실행이 끝날 때까지 걸린 시간. 자식들이 병렬로 돈 것이 반영된 값이다. */
  durationMs: number;
  inputTokens: number;
  outputTokens: number;
  /** 아직 돌고 있는 실행이 있는지. */
  running: boolean;
}

/** 실행 트리 하나의 합계. 화면 위쪽 요약줄과 채팅 카드에 쓴다. */
export function summarize(executions: Execution[]): ExecutionTotals {
  const sum = <T>(pick: (execution: Execution) => T, add: (a: T, b: T) => T, zero: T): T =>
    executions.reduce((acc, execution) => add(acc, pick(execution)), zero);

  const root = executions.find((execution) => execution.parentExecutionId === null);
  return {
    executions: executions.length,
    delegations: executions.filter((execution) => execution.decision?.action === 'delegate').length,
    costUsd: sum((e) => e.metrics?.costUsd ?? 0, (a, b) => a + b, 0),
    durationMs: root?.metrics?.durationMs ?? 0,
    inputTokens: sum((e) => e.metrics?.inputTokens ?? 0, (a, b) => a + b, 0),
    outputTokens: sum((e) => e.metrics?.outputTokens ?? 0, (a, b) => a + b, 0),
    running: executions.some((e) => isLive(e.status)),
  };
}

/** 아직 끝나지 않은 실행인지(대기·실행 중·하위 대기·입력 대기). 에이전트의 "작업 중" 판정에 쓴다. */
export function isLive(status: ExecutionStatus): boolean {
  return status === 'QUEUED' || status === 'RUNNING' || status === 'WAITING_CHILD' || status === 'WAITING_INPUT';
}

/**
 * 프로젝트의 실행 중 사람의 답을 기다리는 것들(입력 대기). **부모·자식 모두** 포함한다 —
 * 위임받은 자식이 사람에게 물으면 그것도 함께 보여 줘야 한다(마스터만 보면 놓친다).
 */
export function waitingInputs(executions: Execution[]): Execution[] {
  return executions.filter((execution) => execution.status === 'WAITING_INPUT');
}

/** 입력 대기 항목에 보여 줄 질문 문장(계약 `ask` 의 question, 없으면 그 실행이 받은 지시). */
export function questionText(execution: Execution): string {
  return execution.question ?? execution.prompt;
}

export function formatCost(usd: number): string {
  return `$${usd.toFixed(2)}`;
}

export function formatTokens(count: number): string {
  return count.toLocaleString('en-US');
}

export function formatDuration(ms: number): string {
  const seconds = Math.round(ms / 1000);
  if (seconds < 60) return `${seconds}초`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}분 ${seconds % 60}초`;
  return `${Math.floor(minutes / 60)}시간 ${minutes % 60}분`;
}
