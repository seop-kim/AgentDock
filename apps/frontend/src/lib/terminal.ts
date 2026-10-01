import type { Execution, Task } from '../types';

/**
 * 터미널이 무엇을 보여 줄지 정하는 상태.
 *  - `live`: 지금 돌고 있다(맡은 Task 가 진행/대기 중이거나 실행이 진행 중) → 로그가 계속 늘어난다.
 *  - `done`: 끝난 실행의 출력을 보여 준다.
 *  - `idle`: 보여 줄 실행이 없다.
 * Task 가 진행 중이면 "지금 하는 일"이 우선이다(마지막 실행이 이미 끝났어도 터미널은 살아 있다).
 */
export type TerminalState = 'live' | 'done' | 'idle';

export function terminalState(execution: Execution | null, task: Task | null): TerminalState {
  if (task && (task.status === 'RUNNING' || task.status === 'PENDING')) return 'live';
  if (!execution) return 'idle';
  return execution.status === 'DONE' || execution.status === 'FAILED' ? 'done' : 'live';
}

/** 에이전트가 "지금 돌리는" 실행을 고른다. 진행 중인 것을 먼저 보고, 없으면 가장 최근 실행. */
export function currentExecution(executions: Execution[], agentId: number): Execution | null {
  const mine = executions.filter((execution) => execution.agentId === agentId);
  if (mine.length === 0) return null;
  const running = mine.filter(
    (execution) =>
      execution.status === 'QUEUED' || execution.status === 'RUNNING' || execution.status === 'WAITING_CHILD',
  );
  return running.length > 0 ? running[running.length - 1] : mine[mine.length - 1];
}

/** 에이전트가 맡은 Task(가장 최근 것). */
export function currentTask(tasks: Task[], agentId: number): Task | null {
  const mine = tasks.filter((task) => task.agentId === agentId);
  return mine.length > 0 ? mine[mine.length - 1] : null;
}

/** 터미널 한 줄. 색만 구분하고 모양은 `styles/terminal.module.css` 가 정한다. */
export interface TerminalLine {
  text: string;
  kind: 'prompt' | 'tool' | 'plain' | 'done' | 'muted';
}

/**
 * 실행 로그 한 줄이 화면에서 어떤 색인지 고른다.
 * 백엔드가 SSE/조회로 주는 `stream` 값은 소문자다(`stdout`/`stderr`/`system`).
 */
export function logLineKind(stream: string): TerminalLine['kind'] {
  switch ((stream ?? '').toLowerCase()) {
    case 'system':
      return 'muted';
    case 'stderr':
      return 'tool';
    default:
      return 'plain';
  }
}
