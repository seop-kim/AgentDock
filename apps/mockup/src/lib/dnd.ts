import type { DragEvent } from 'react';

/** 에이전트 드래그 데이터. fromGroupId 가 있으면 그 그룹의 멤버 칩을 끌고 있는 것이다. */
export interface AgentDragPayload {
  agentId: number;
  fromGroupId?: number;
}

const MIME = 'application/x-agentdock-agent';

export function startAgentDrag(event: DragEvent, payload: AgentDragPayload) {
  event.dataTransfer.setData(MIME, JSON.stringify(payload));
  event.dataTransfer.effectAllowed = 'move';
}

export function isAgentDrag(event: DragEvent): boolean {
  return Array.from(event.dataTransfer.types).includes(MIME);
}

export function readAgentDrag(event: DragEvent): AgentDragPayload | null {
  const raw = event.dataTransfer.getData(MIME);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AgentDragPayload;
  } catch {
    return null;
  }
}
