import { DragEvent, useState } from 'react';
import { isAgentDrag, readAgentDrag, startAgentDrag } from '../../lib/dnd';
import { unavailableReason } from '../../lib/agentAvailability';
import { SEED_ROLES } from '../../store/seed';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import type { ChatTarget, Project } from '../../types';
import styles from './AgentList.module.css';
import NewAgentModal from './NewAgentModal';

/**
 * 프로젝트 상세에서 구성도 위에 떠 있는 에이전트 패널. 카드를 구성도의 그룹으로 끌어 놓아 멤버로 넣는다(그룹은 필수가 아니다).
 * 캔버스의 멤버 노드를 이 목록으로 끌어 놓으면 그 그룹에서 빠진다.
 */
export default function AgentList({
  project,
  target,
  onSelect,
  onCollapse,
}: {
  project: Project;
  /** 채팅 명령의 현재 대상(카드를 누르면 바뀐다) */
  target: ChatTarget | null;
  onSelect: (target: ChatTarget) => void;
  onCollapse: () => void;
}) {
  const { agents, groups, providers, removeGroupMember } = useMockStore();
  const [creating, setCreating] = useState(false);
  const [dropActive, setDropActive] = useState(false);

  const projectAgents = agents.filter((a) => a.projectId === project.id);
  const projectGroups = groups.filter((g) => g.projectId === project.id);

  const onDragOver = (e: DragEvent) => {
    if (!isAgentDrag(e)) return;
    e.preventDefault();
    setDropActive(true);
  };

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDropActive(false);
    const payload = readAgentDrag(e);
    if (payload?.fromGroupId !== undefined) removeGroupMember(payload.fromGroupId, payload.agentId);
  };

  return (
    <aside
      className={`${styles.list} ${dropActive ? styles.dropActive : ''}`}
      onDragOver={onDragOver}
      onDragLeave={() => setDropActive(false)}
      onDrop={onDrop}
    >
      <div className={styles.header}>
        <h2 className={styles.title}>
          에이전트 <span className={shared.muted}>{projectAgents.length}</span>
        </h2>
        <div className={styles.headerActions}>
          <button onClick={() => setCreating(true)}>+ 새 에이전트</button>
          <button type="button" className={styles.collapse} onClick={onCollapse} aria-label="에이전트 패널 접기" title="접기">
            ‹
          </button>
        </div>
      </div>

      {projectAgents.length === 0 && (
        <p className={shared.muted}>에이전트가 없습니다. "새 에이전트"로 만들어 보세요.</p>
      )}

      <div className={styles.cards}>
        {projectAgents.map((agent) => {
          const provider = providers.find((p) => p.id === agent.providerId);
          const role = SEED_ROLES.find((r) => r.id === agent.roleId);
          const memberOf = projectGroups.filter((g) => g.memberIds.includes(agent.id));
          const reason = unavailableReason(agent, providers, project);
          return (
            <div
              key={agent.id}
              className={`${styles.card} ${target?.kind === 'agent' && target.id === agent.id ? styles.cardSelected : ''}`}
              draggable
              onDragStart={(e) => startAgentDrag(e, { agentId: agent.id })}
              onClick={() => onSelect({ kind: 'agent', id: agent.id })}
              title="누르면 채팅 대상으로 선택됩니다"
            >
              <div className={styles.cardTop}>
                <span className={styles.grip} aria-hidden="true">
                  ⋮⋮
                </span>
                <strong className={styles.name}>{agent.name}</strong>
                {reason && <span className={`${shared.badge} ${shared.badgeError}`}>{reason}</span>}
              </div>
              <div className={shared.muted}>
                {role?.name} · {provider?.name ?? '삭제된 런타임'}
              </div>
              <div className={shared.muted}>
                {agent.model || 'CLI 기본 모델'} / {agent.mode || 'CLI 기본 모드'}
              </div>
              <div className={styles.groups}>
                {memberOf.length === 0 ? (
                  <span className={styles.ungrouped}>그룹 없음</span>
                ) : (
                  memberOf.map((g) => (
                    <span key={g.id} className={styles.groupChip}>
                      {g.name}
                    </span>
                  ))
                )}
              </div>
            </div>
          );
        })}
      </div>

      {projectAgents.length > 0 && (
        <p className={styles.tip}>카드를 그룹으로 끌어 놓으면 멤버가 됩니다. 그룹에 속하지 않아도 괜찮습니다. 카드를 누르면 채팅 대상이 됩니다.</p>
      )}

      {creating && <NewAgentModal projectId={project.id} onClose={() => setCreating(false)} />}
    </aside>
  );
}
