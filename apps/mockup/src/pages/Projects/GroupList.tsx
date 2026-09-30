import { DragEvent, FormEvent, useState } from 'react';
import { isAgentDrag, startAgentDrag } from '../../lib/dnd';
import { useGroupDrop } from '../../lib/useGroupDrop';
import { PlusIcon } from '../../components/icons';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import type { ChatTarget, Project } from '../../types';
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
  const { agents, groups, createGroup, deleteGroup, removeGroupMember } = useMockStore();
  const dropOnGroup = useGroupDrop(onNotice);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [overId, setOverId] = useState<number | null>(null);

  const projectGroups = groups.filter((g) => g.projectId === project.id);
  const agentName = (id: number) => agents.find((a) => a.id === id)?.name ?? '?';

  const onCreate = (e: FormEvent) => {
    e.preventDefault();
    createGroup(project.id, name.trim());
    setName('');
    setAdding(false);
  };

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
        {!adding && (
          <button
            type="button"
            className={shared.addButton}
            onClick={() => setAdding(true)}
            aria-label="새 그룹"
            title="새 그룹"
          >
            <PlusIcon />
          </button>
        )}
      </div>

      {adding && (
        <form onSubmit={onCreate} className={styles.createForm}>
          <input
            autoFocus
            placeholder="그룹 이름 (예: Backend Team)"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
          <button type="submit">추가</button>
          <button
            type="button"
            onClick={() => {
              setAdding(false);
              setName('');
            }}
          >
            취소
          </button>
        </form>
      )}

      {projectGroups.length === 0 && !adding && (
        <p className={shared.muted}>그룹이 없습니다. 에이전트는 그룹 없이도 쓸 수 있습니다.</p>
      )}

      <div className={styles.cards}>
        {projectGroups.map((group) => {
          const selected = target?.kind === 'group' && target.id === group.id;
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
                멤버 {group.memberIds.length} · 리더{' '}
                {group.leaderAgentId !== null ? agentName(group.leaderAgentId) : '없음'}
              </div>
              <div className={styles.members}>
                {group.memberIds.length === 0 && <span className={styles.emptyDrop}>에이전트를 여기로 끌어 놓으세요</span>}
                {group.memberIds.map((agentId) => (
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
    </aside>
  );
}
