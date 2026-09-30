import { TEAM_FILES } from './executionSim';
import type { Agent, AgentGroup, Execution, Task } from '../types';

/**
 * 터미널이 무엇을 보여 줄지 정하는 상태.
 *  - `live`: 지금 돌고 있다(맡은 Task 가 진행/대기 중이거나 실행이 진행 중) → 줄이 계속 늘어난다.
 *  - `done`: 끝난 실행의 출력을 보여 준다(완료 요약·지표 포함).
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

/** 터미널 한 줄. 색만 구분하고 모양은 CSS 가 정한다. */
export interface TerminalLine {
  text: string;
  kind: 'prompt' | 'tool' | 'plain' | 'done' | 'muted';
}

/** 돌고 있는 동안 한 줄씩 덧붙이는 모의 출력(살아 있는 느낌을 준다). */
const TAIL: TerminalLine[] = [
  { text: '✻ 생각하는 중…', kind: 'plain' },
  { text: '● Read(src/main/java/com/shop/api/InventoryService.java)', kind: 'tool' },
  { text: '✻ …', kind: 'plain' },
  { text: '● Bash(npm test)', kind: 'tool' },
  { text: '  ⎿ 12 passed', kind: 'muted' },
  { text: '✻ 정리하는 중…', kind: 'plain' },
];

/** 덧붙일 다음 줄(모의). 같은 step 이면 같은 줄이 나온다. */
export function tailLine(step: number): TerminalLine {
  return TAIL[step % TAIL.length];
}

/** 그 에이전트가 실제로 돌린 것처럼 보이는 터미널 줄들(모의). 같은 상태면 같은 줄이 나온다. */
export function terminalLines(input: {
  agent: Agent;
  execution: Execution | null;
  task: Task | null;
  groups: AgentGroup[];
  /** 실행 폴더(프로젝트 기본 워크스페이스 경로) */
  cwd: string | null;
}): TerminalLine[] {
  const { agent, execution, task, groups, cwd } = input;
  const state = terminalState(execution, task);
  if (state === 'idle') return [{ text: '지금 실행 중인 작업이 없습니다.', kind: 'muted' }];

  const teamName = groups.find((group) => group.memberIds.includes(agent.id))?.name ?? null;
  // 지금 하는 일(Task)이 있으면 그것이 지시문이고, 아니면 마지막 실행의 지시문을 쓴다.
  const instruction = (state === 'live' ? task?.title : execution?.prompt) ?? execution?.prompt ?? task?.title ?? '';
  const changed = execution?.handoff?.changedFiles ?? [];
  const files = changed.length > 0 ? changed : (teamName ? TEAM_FILES[teamName] : undefined) ?? [];

  const lines: TerminalLine[] = [
    { text: `$ claude -p ${truncate(instruction)} --output-format json`, kind: 'prompt' },
    { text: `# cwd: ${cwd ?? '(워크스페이스 없음)'}`, kind: 'muted' },
    { text: '✻ 대화를 시작합니다…', kind: 'plain' },
  ];

  files.slice(0, 2).forEach((file) => lines.push({ text: `● Read(${file})`, kind: 'tool' }));

  const decision = execution?.decision ?? null;
  if (decision?.action === 'delegate') {
    lines.push({
      text: `→ 위임: 에이전트 #${decision.targetAgentId} · ${truncate(decision.prompt, 40)}`,
      kind: 'tool',
    });
  }

  if (state === 'live') {
    if (execution?.status === 'WAITING_CHILD') {
      lines.push({ text: '⎿ 하위 실행이 끝나기를 기다리는 중…', kind: 'muted' });
    } else {
      lines.push({ text: '✻ 작업 중…', kind: 'plain' });
    }
    return lines;
  }

  lines.push({ text: `● 완료 — ${execution?.handoff?.summary ?? '작업을 마쳤습니다.'}`, kind: 'done' });
  if (execution?.metrics) {
    lines.push({
      text: `⎿ $${execution.metrics.costUsd.toFixed(2)} · ${Math.round(execution.metrics.durationMs / 1000)}초 · 세션 ${execution.sessionId.slice(0, 8)}`,
      kind: 'muted',
    });
  }
  return lines;
}

function truncate(text: string, max = 60): string {
  const oneLine = text.replace(/\s+/g, ' ').trim();
  return oneLine.length > max ? `${oneLine.slice(0, max)}…` : oneLine;
}
