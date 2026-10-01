import type { Agent, AiProvider, Project } from '../types';

/**
 * 에이전트를 새로 실행할 수 없는 사유(없으면 null). 백엔드의 AgentAvailability 를 화면에서 흉내 낸 것으로,
 * 런타임이 삭제/꺼짐이거나 프로젝트에 기본 워크스페이스가 없으면 막힌다.
 */
export function unavailableReason(agent: Agent, providers: AiProvider[], project: Project | undefined): string | null {
  const provider = providers.find((p) => p.id === agent.providerId);
  if (!provider) return '런타임 삭제됨';
  if (!provider.enabled) return '런타임 꺼짐';
  if (!project?.workspaces.some((w) => w.isDefault)) return '워크스페이스 없음';
  return null;
}
