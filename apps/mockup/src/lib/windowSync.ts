import { readStorage, STORAGE_KEYS, writeStorage } from './storage';
import type { Agent, AgentGroup, Execution, Project, Task, Workspace } from '../types';

/**
 * 터미널을 **새 창**으로 띄울 때 창끼리 상태를 넘기는 묶음.
 * 목업 상태는 메모리에만 있으므로, 메인 창이 자기 상태를 저장해 두고 새 창이 그것을 읽는다.
 * 실제 구현에서는 새 창이 실행 로그(SSE)를 직접 구독하므로 이 다리가 필요 없다.
 */
export interface MockSnapshot {
  agents: Agent[];
  groups: AgentGroup[];
  executions: Execution[];
  tasks: Task[];
  projects: Project[];
  workspaces: Workspace[];
}

const CHANNEL_NAME = 'agentdock-mockup';

let channel: BroadcastChannel | null | undefined;

function getChannel(): BroadcastChannel | null {
  if (channel !== undefined) return channel;
  try {
    channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL_NAME);
  } catch {
    channel = null;
  }
  return channel;
}

/** 메인 창: 상태가 바뀔 때마다 저장해 두고 새 창에 알린다. */
export function publishSnapshot(snapshot: MockSnapshot) {
  writeStorage(STORAGE_KEYS.terminalSnapshot, JSON.stringify(snapshot));
  getChannel()?.postMessage(snapshot);
}

/** 새 창: 바뀔 때마다 최신 상태를 받는다. */
export function subscribeSnapshot(onSnapshot: (snapshot: MockSnapshot) => void): () => void {
  const bus = getChannel();
  if (!bus) return () => {};
  const onMessage = (event: MessageEvent<MockSnapshot>) => onSnapshot(event.data);
  bus.addEventListener('message', onMessage);
  return () => bus.removeEventListener('message', onMessage);
}

/** 새 창: 저장된 마지막 상태(창을 나중에 열어도 최신이다). */
export function readSnapshot(): MockSnapshot | null {
  const raw = readStorage(STORAGE_KEYS.terminalSnapshot);
  if (raw === null) return null;
  try {
    return JSON.parse(raw) as MockSnapshot;
  } catch {
    return null;
  }
}

/**
 * 그 에이전트의 터미널을 **새 창**으로 띄운다.
 * 메뉴 클릭(사용자 제스처) 안에서 부르므로 팝업 차단에 걸리지 않는다. 같은 에이전트는 같은 창을 다시 쓴다.
 */
export function openTerminalWindow(agentId: number) {
  window.open(`/terminal/${agentId}`, `agentdock-terminal-${agentId}`, 'width=860,height=640');
}
