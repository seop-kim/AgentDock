import { DragEvent, FormEvent, useState } from 'react';
import { isAgentDrag, readAgentDrag, startAgentDrag } from '../../lib/dnd';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import type { AgentGroup, Project } from '../../types';
import styles from './GroupBoard.module.css';

/**
 * 프로젝트 상세 오른쪽의 그룹 영역. 왼쪽 목록의 에이전트를 그룹 카드로 끌어 놓으면 멤버로 들어간다.
 * 멤버 칩은 다른 그룹으로 끌어 옮기거나 왼쪽 목록으로 끌어 빼낼 수 있다.
 */
export default function GroupBoard({ project, onNotice }: { project: Project; onNotice: (message: string) => void }) {
  const { agents, groups, createGroup, deleteGroup, addGroupMember, removeGroupMember, setGroupLeader } = useMockStore();
  const [name, setName] = useState('');
  const [overGroupId, setOverGroupId] = useState<number | null>(null);

  const projectGroups = groups.filter((g) => g.projectId === project.id);
  const agentName = (id: number) => agents.find((a) => a.id === id)?.name ?? '?';

  const onCreate = (e: FormEvent) => {
    e.preventDefault();
    createGroup(project.id, name.trim());
    setName('');
  };

  const onDragOver = (e: DragEvent, group: AgentGroup) => {
    if (!isAgentDrag(e)) return;
    e.preventDefault();
    setOverGroupId(group.id);
  };

  const onDrop = (e: DragEvent, group: AgentGroup) => {
    e.preventDefault();
    setOverGroupId(null);
    const payload = readAgentDrag(e);
    if (!payload) return;
    if (group.memberIds.includes(payload.agentId)) {
      onNotice(`${agentName(payload.agentId)} 은(는) 이미 ${group.name} 의 멤버입니다.`);
      return;
    }
    addGroupMember(group.id, payload.agentId);
    // 다른 그룹의 칩을 끌어 온 경우는 "옮기기"이므로 원래 그룹에서는 뺀다.
    if (payload.fromGroupId !== undefined && payload.fromGroupId !== group.id) {
      removeGroupMember(payload.fromGroupId, payload.agentId);
    }
  };

  const onDelete = (group: AgentGroup) => {
    if (!window.confirm(`"${group.name}" 그룹을 삭제할까요? 에이전트는 삭제되지 않습니다.`)) return;
    deleteGroup(group.id);
  };

  return (
    <section className={styles.board}>
      <div className={styles.header}>
        <h2 className={styles.title}>
          그룹 <span className={shared.muted}>{projectGroups.length}</span>
        </h2>
        <form onSubmit={onCreate} className={styles.createForm}>
          <input placeholder="새 그룹 이름 (예: Backend Team)" value={name} onChange={(e) => setName(e.target.value)} required />
          <button type="submit">+ 그룹 추가</button>
        </form>
      </div>

      {projectGroups.length === 0 && (
        <p className={shared.muted}>그룹이 없습니다. 그룹을 추가하고 에이전트를 끌어 넣어 팀을 구성하세요.</p>
      )}

      <div className={styles.grid}>
        {projectGroups.map((group) => (
          <div
            key={group.id}
            className={`${styles.group} ${overGroupId === group.id ? styles.groupOver : ''}`}
            onDragOver={(e) => onDragOver(e, group)}
            onDragLeave={() => setOverGroupId(null)}
            onDrop={(e) => onDrop(e, group)}
          >
            <div className={styles.groupHeader}>
              <div>
                <strong>{group.name}</strong>
                <div className={shared.muted}>
                  멤버 {group.memberIds.length} · 리더{' '}
                  {group.leaderAgentId !== null ? agentName(group.leaderAgentId) : '없음'}
                </div>
              </div>
              <button className={shared.dangerButton} onClick={() => onDelete(group)}>
                삭제
              </button>
            </div>

            <div className={styles.members}>
              {group.memberIds.length === 0 && <span className={styles.emptyDrop}>에이전트를 여기로 끌어 놓으세요</span>}
              {group.memberIds.map((agentId) => {
                const isLeader = group.leaderAgentId === agentId;
                return (
                  <span
                    key={agentId}
                    className={styles.member}
                    draggable
                    onDragStart={(e) => startAgentDrag(e, { agentId, fromGroupId: group.id })}
                  >
                    <button
                      type="button"
                      className={`${styles.iconButton} ${isLeader ? styles.leader : ''}`}
                      title={isLeader ? '리더' : '리더로 지정'}
                      aria-label={isLeader ? `${agentName(agentId)} 리더` : `${agentName(agentId)} 리더로 지정`}
                      onClick={() => setGroupLeader(group.id, agentId)}
                    >
                      {isLeader ? '★' : '☆'}
                    </button>
                    {agentName(agentId)}
                    <button
                      type="button"
                      className={styles.iconButton}
                      title="그룹에서 제거"
                      aria-label={`${agentName(agentId)} 그룹에서 제거`}
                      onClick={() => removeGroupMember(group.id, agentId)}
                    >
                      ×
                    </button>
                  </span>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
