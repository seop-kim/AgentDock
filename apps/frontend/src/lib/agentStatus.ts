import type { Agent, Task } from '../types';

export type AgentStatusKind = 'UNPLACED' | 'WORKING' | 'WAITING' | 'IDLE';

export interface AgentStatus {
  kind: AgentStatusKind;
  /** 화면에 그대로 쓰는 한국어 이름. */
  label: string;
}

const LABELS: Record<AgentStatusKind, string> = {
  UNPLACED: '미배치',
  WORKING: '작업 중',
  WAITING: '작업 대기중',
  IDLE: '작업 없음',
};

/**
 * 에이전트의 지금 상태.
 *  - 구성도에 없으면(placed=false) **미배치**.
 *  - 구성도에 있으면 맡은 Task 로 판단한다: 진행 중(RUNNING)이면 **작업 중**, 대기(PENDING)가 있으면 **작업 대기중**,
 *    둘 다 없으면 **작업 없음**. 완료(DONE)/실패(FAILED)만 있으면 작업 없음이다.
 */
export function agentStatus(agent: Agent, tasks: Task[]): AgentStatus {
  const kind = statusKind(agent, tasks);
  return { kind, label: LABELS[kind] };
}

function statusKind(agent: Agent, tasks: Task[]): AgentStatusKind {
  if (!agent.placed) return 'UNPLACED';
  const mine = tasks.filter((t) => t.agentId === agent.id);
  if (mine.some((t) => t.status === 'RUNNING')) return 'WORKING';
  if (mine.some((t) => t.status === 'PENDING')) return 'WAITING';
  return 'IDLE';
}
