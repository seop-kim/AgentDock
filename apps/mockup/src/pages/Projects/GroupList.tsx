import { DragEvent, useState } from 'react';
import { isAgentDrag, startAgentDrag } from '../../lib/dnd';
import { useGroupDrop } from '../../lib/useGroupDrop';
import { PlusIcon } from '../../components/icons';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import type { ChatTarget, Project } from '../../types';
import GroupFormModal from './GroupFormModal';
import styles from './GroupList.module.css';

/**
 * 에이전트 목록 아래의 그룹 목록. 그룹을 만들고 지우며, 에이전트 카드를 그룹 카드로 끌어 놓으면 멤버가 된다.
 * 그룹 카드를 누르면 채팅 대상이 되어 그 그룹의 리더가 명령을 받는다.
 */
export default function GroupList({
  project,
  target,
  onSelect,
  onNotice,
}: {
  project: Project;
  target: ChatTarget | null;
  onSelect: (target: ChatTarget) => void;
  onNotice: (message: string) => void;
}) {
  const { agents, groups, deleteGroup, removeGroupMember } = useMockStore();
  const dropOnGroup = useGroupDrop(onNotice);
  const [creating, setCreating] = useState(false);
  const [overId, setOverId] = useState<number | null>(null);

  const projectGroups = groups.filter((g) => g.projectId === project.id);
  const agentName = (id: number) => agents.find((a) => a.id === id)?.name ?? '?';
  // 구성도에서 빼 둔(미배치) 에이전트는 소속만 유지하고 목록/구성도에는 보이지 않는다.
  const placedIds = new Set(agents.filter((a) => a.placed).map((a) => a.id));

  const onDragOver = (e: DragEvent, groupId: number) => {
    if (!isAgentDrag(e)) return;
    e.preventDefault();
    setOverId(groupId);
  };

  const onDrop = (e: DragEvent, groupId: number) => {
    setOverId(null);
    dropOnGroup(e, groupId);
  };

  const onDelete = (id: number, groupName: string) => {
    if (!window.confirm(`"${groupName}" 그룹을 삭제할까요? 에이전트는 삭제되지 않습니다.`)) return;
    deleteGroup(id);
  };

  return (
    <aside className={styles.list}>
      <div className={styles.header}>
        <h2 className={styles.title}>
          그룹 <span className={shared.muted}>{projectGroups.length}</span>
        </h2>
        <button
          type="button"
          className={shared.addButton}
          onClick={() => setCreating(true)}
          aria-label="새 그룹"
          title="새 그룹"
        >
          <PlusIcon />
        </button>
      </div>

      {projectGroups.length === 0 && (
        <p className={shared.muted}>그룹이 없습니다. 에이전트는 그룹 없이도 쓸 수 있습니다.</p>
      )}

      <div className={styles.cards}>
        {projectGroups.map((group) => {
          const selected = target?.kind === 'group' && target.id === group.id;
          const memberIds = group.memberIds.filter((id) => placedIds.has(id));
          const leaderName =
            group.leaderAgentId !== null && placedIds.has(group.leaderAgentId)
              ? agentName(group.leaderAgentId)
              : '없음';
          return (
            <div
              key={group.id}
              className={`${styles.card} ${selected ? styles.cardSelected : ''} ${overId === group.id ? styles.cardOver : ''}`}
              onClick={() => onSelect({ kind: 'group', id: group.id })}
              onDragOver={(e) => onDragOver(e, group.id)}
              onDragLeave={() => setOverId(null)}
              onDrop={(e) => onDrop(e, group.id)}
              title="누르면 채팅 대상으로 선택됩니다"
            >
              <div className={styles.cardTop}>
                <strong className={styles.name}>{group.name}</strong>
                <button
                  type="button"
                  className={styles.delete}
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(group.id, group.name);
                  }}
                  aria-label={`${group.name} 그룹 삭제`}
                >
                  삭제
                </button>
              </div>
              <div className={shared.muted}>
                멤버 {memberIds.length} · 리더 {leaderName}
              </div>
              <div className={styles.members}>
                {memberIds.length === 0 && <span className={styles.emptyDrop}>에이전트를 여기로 끌어 놓으세요</span>}
                {memberIds.map((agentId) => (
                  <span
                    key={agentId}
                    className={styles.member}
                    draggable
                    onDragStart={(e) => startAgentDrag(e, { agentId, fromGroupId: group.id })}
                    onClick={(e) => e.stopPropagation()}
                  >
                    {group.leaderAgentId === agentId && <span className={styles.leader}>★</span>}
                    {agentName(agentId)}
                    <button
                      type="button"
                      className={styles.remove}
                      title="그룹에서 제거"
                      aria-label={`${agentName(agentId)} 그룹에서 제거`}
                      onClick={(e) => {
                        e.stopPropagation();
                        removeGroupMember(group.id, agentId);
                      }}
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            </div>
          );
        })}
      </div>

      {creating && <GroupFormModal project={project} onClose={() => setCreating(false)} />}
    </aside>
  );
}
