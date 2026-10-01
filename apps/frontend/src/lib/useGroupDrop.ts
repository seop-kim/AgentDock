import type { DragEvent } from 'react';
import { useAgentDockStore } from '../store/AgentDockStore';
import { readAgentDrag } from './dnd';

/**
 * 에이전트를 그룹에 놓았을 때의 공통 처리(구성도의 그룹 상자, 왼쪽 그룹 카드가 함께 쓴다).
 * 이미 멤버면 안내만 하고, 다른 그룹에서 끌어 온 경우는 원래 그룹에서 빼서 "옮기기"로 처리한다.
 */
export function useGroupDrop(onNotice: (message: string) => void) {
  const { agents, groups, projects, addGroupMember, removeGroupMember } = useAgentDockStore();

  return (e: DragEvent, groupId: number) => {
    e.preventDefault();
    const payload = readAgentDrag(e);
    const group = groups.find((g) => g.id === groupId);
    if (!payload || !group) return;
    const name = agents.find((a) => a.id === payload.agentId)?.name;
    // 마스터는 프로젝트 최상위 리더라 그룹에 속하지 않는다.
    const project = projects.find((p) => p.id === group.projectId);
    if (project?.masterAgentId === payload.agentId) {
      onNotice(`${name} 은(는) 프로젝트 마스터라 그룹에 넣을 수 없습니다.`);
      return;
    }
    if (group.memberIds.includes(payload.agentId)) {
      onNotice(`${name} 은(는) 이미 ${group.name} 의 멤버입니다.`);
      return;
    }
    addGroupMember(groupId, payload.agentId);
    if (payload.fromGroupId !== undefined && payload.fromGroupId !== groupId) {
      removeGroupMember(payload.fromGroupId, payload.agentId);
    }
  };
}
