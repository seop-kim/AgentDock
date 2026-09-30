import { DragEvent, useEffect, useRef, useState } from 'react';
import { unavailableReason } from '../../lib/agentAvailability';
import { agentStatus } from '../../lib/agentStatus';
import { isAgentDrag, readAgentDrag, startAgentDrag } from '../../lib/dnd';
import { PlusIcon } from '../../components/icons';
import { useMockStore } from '../../store/MockStore';
import { SEED_ROLES } from '../../store/seed';
import shared from '../../styles/shared.module.css';
import type { Agent, Project } from '../../types';
import AgentCardMenu from './AgentCardMenu';
import AgentFormModal from './AgentFormModal';
import AgentHoverCard from './AgentHoverCard';
import styles from './AgentList.module.css';

/** 마우스를 올린 뒤 정보 창이 뜨기까지의 지연(스쳐 지나갈 때 깜빡이지 않게) */
const HOVER_DELAY_MS = 250;

/**
 * 프로젝트 상세에서 구성도 위에 떠 있는 에이전트 패널. 카드는 이름과 역할만 보이는 작은 카드다.
 *  - 마우스를 올리면 이름/역할/연결된 에이전트/프롬프트 정보 창이 뜬다.
 *  - 누르면 선택되고 구성도가 그 에이전트로 이동한다. 선택된 카드를 다시 누르면 선택이 풀린다.
 *  - "…" 메뉴에서 상세 설정과 삭제를 한다.
 *  - 카드를 구성도/그룹 카드의 그룹으로 끌어 놓으면 멤버가 되고(그룹은 필수가 아니다),
 *    그룹 멤버 칩을 이 패널로 끌어 놓으면 그 그룹에서 빠진다.
 */
export default function AgentList({
  project,
  selectedAgentId,
  onSelectAgent,
}: {
  project: Project;
  selectedAgentId: number | null;
  onSelectAgent: (agentId: number) => void;
}) {
  const { agents, groups, providers, tasks, removeGroupMember, deleteAgent, setAgentPlaced } = useMockStore();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Agent | null>(null);
  const [dropActive, setDropActive] = useState(false);
  const [hover, setHover] = useState<{ agent: Agent; rect: DOMRect } | null>(null);
  const [menu, setMenu] = useState<{ agent: Agent; rect: DOMRect } | null>(null);
  const hoverTimer = useRef<number | undefined>(undefined);

  const projectAgents = agents.filter((a) => a.projectId === project.id);

  useEffect(() => () => window.clearTimeout(hoverTimer.current), []);

  const showHover = (agent: Agent, el: HTMLElement) => {
    window.clearTimeout(hoverTimer.current);
    hoverTimer.current = window.setTimeout(() => setHover({ agent, rect: el.getBoundingClientRect() }), HOVER_DELAY_MS);
  };

  const hideHover = () => {
    window.clearTimeout(hoverTimer.current);
    setHover(null);
  };

  const onDelete = (agent: Agent) => {
    const memberOf = groups.filter((g) => g.memberIds.includes(agent.id)).length;
    const extra = memberOf > 0 ? `\n${memberOf}개 그룹에서도 빠집니다.` : '';
    if (!window.confirm(`"${agent.name}" 에이전트를 삭제할까요?${extra}`)) return;
    deleteAgent(agent.id);
  };

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
        <button
          type="button"
          className={shared.addButton}
          onClick={() => setCreating(true)}
          aria-label="새 에이전트"
          title="새 에이전트"
        >
          <PlusIcon />
        </button>
      </div>

      {projectAgents.length === 0 && <p className={shared.muted}>에이전트가 없습니다. + 버튼으로 만들어 보세요.</p>}

      <div className={styles.cards}>
        {projectAgents.map((agent) => {
          const role = SEED_ROLES.find((r) => r.id === agent.roleId);
          const reason = unavailableReason(agent, providers, project);
          const status = agentStatus(agent, tasks);
          const statusClass = {
            UNPLACED: styles.statusUnplaced,
            WORKING: styles.statusWorking,
            WAITING: styles.statusWaiting,
            IDLE: styles.statusIdle,
          }[status.kind];
          const selected = selectedAgentId === agent.id;
          return (
            <div
              key={agent.id}
              className={`${styles.card} ${selected ? styles.cardSelected : ''}`}
              draggable
              aria-pressed={selected}
              onDragStart={(e) => {
                hideHover();
                startAgentDrag(e, { agentId: agent.id });
              }}
              onClick={() => {
                hideHover();
                onSelectAgent(agent.id);
              }}
              onMouseEnter={(e) => showHover(agent, e.currentTarget)}
              onMouseLeave={hideHover}
            >
              <span className={styles.grip} aria-hidden="true">
                ⋮⋮
              </span>
              <div className={styles.text}>
                <strong className={styles.name}>{agent.name}</strong>
                <span className={styles.role}>{role?.name}</span>
              </div>
              <span className={`${styles.status} ${statusClass}`} title={`상태: ${status.label}`}>
                {status.label}
              </span>
              {reason && <span className={styles.warnDot} aria-label={reason} />}
              <button
                type="button"
                className={styles.menuButton}
                aria-label={`${agent.name} 메뉴`}
                aria-haspopup="menu"
                onClick={(e) => {
                  e.stopPropagation();
                  hideHover();
                  setMenu({ agent, rect: e.currentTarget.getBoundingClientRect() });
                }}
              >
                …
              </button>
            </div>
          );
        })}
      </div>

      {hover && <AgentHoverCard agent={hover.agent} project={project} anchor={hover.rect} />}
      {menu && (
        <AgentCardMenu
          anchor={menu.rect}
          placed={menu.agent.placed}
          onClose={() => setMenu(null)}
          onEdit={() => {
            setEditing(menu.agent);
            setMenu(null);
          }}
          onTogglePlaced={() => {
            const target = menu.agent;
            setMenu(null);
            setAgentPlaced(target.id, !target.placed);
          }}
          onDelete={() => {
            const target = menu.agent;
            setMenu(null);
            onDelete(target);
          }}
        />
      )}
      {creating && <AgentFormModal project={project} onClose={() => setCreating(false)} />}
      {editing && <AgentFormModal project={project} agent={editing} onClose={() => setEditing(null)} />}
    </aside>
  );
}
