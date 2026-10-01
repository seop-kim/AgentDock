import type { Agent, Execution, Task } from '../types';
import { isLive } from './executions';

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
 *  - 구성도에 있으면 **실행으로 판단한다**(작업보다 실행이 지금 하는 일에 가깝다): 그 에이전트의 실행 중
 *    아직 끝나지 않은 것(대기/실행 중/하위 대기/입력 대기)이 하나라도 있으면 **작업 중**이다. 위임받은 자식 실행도
 *    그 실행의 담당 에이전트 것이므로 함께 세어진다.
 *  - 실행이 다 끝났는데 맡은 Task 가 대기(PENDING)면 **작업 대기중**, 둘 다 없으면 **작업 없음**.
 */
export function agentStatus(agent: Agent, tasks: Task[], executions: Execution[]): AgentStatus {
  const kind = statusKind(agent, tasks, executions);
  return { kind, label: LABELS[kind] };
}

function statusKind(agent: Agent, tasks: Task[], executions: Execution[]): AgentStatusKind {
  if (!agent.placed) return 'UNPLACED';
  if (executions.some((execution) => execution.agentId === agent.id && isLive(execution.status))) return 'WORKING';
  if (tasks.some((task) => task.agentId === agent.id && task.status === 'PENDING')) return 'WAITING';
  return 'IDLE';
}
