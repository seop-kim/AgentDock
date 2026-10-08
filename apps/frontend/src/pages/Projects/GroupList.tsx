import { DragEvent, useEffect, useRef, useState } from 'react';
import { askConfirm } from '../../components/ConfirmDialog';
import { isAgentDrag, startAgentDrag } from '../../lib/dnd';
import { useGroupDrop } from '../../lib/useGroupDrop';
import { ChevronDownIcon, ChevronUpIcon, PlusIcon } from '../../components/icons';
import { useAgentDockStore } from '../../store/AgentDockStore';
import shared from '../../styles/shared.module.css';
import type { AgentGroup, ChatTarget, Project } from '../../types';
import GroupCardMenu from './GroupCardMenu';
import GroupFormModal from './GroupFormModal';
import GroupHoverCard from './GroupHoverCard';
import GroupPromptModal from './GroupPromptModal';
import styles from './GroupList.module.css';

/** 마우스를 올린 뒤 정보 창이 뜨기까지의 지연(스쳐 지나갈 때 깜빡이지 않게) */
const HOVER_DELAY_MS = 250;

/**
 * 에이전트 목록 아래의 그룹 목록. 그룹을 만들고 지우며, 에이전트 카드를 그룹 카드로 끌어 놓으면 멤버가 된다.
 * 그룹 카드를 누르면 채팅 대상이 되어 그 그룹의 리더가 명령을 받는다.
 */
export default function GroupList({
  project,
  target,
  onSelect,
  onNotice,
  open,
  onToggle,
}: {
  project: Project;
  target: ChatTarget | null;
  onSelect: (target: ChatTarget) => void;
  onNotice: (message: string) => void;
  /** 패널을 펼쳐 둘지(접으면 머리말만 남는다) */
  open: boolean;
  onToggle: () => void;
}) {
  const { agents, groups, deleteGroup, removeGroupMember } = useAgentDockStore();
  const dropOnGroup = useGroupDrop(onNotice);
  const [creating, setCreating] = useState(false);
  const [overId, setOverId] = useState<number | null>(null);
  const [menu, setMenu] = useState<{ group: AgentGroup; rect: DOMRect } | null>(null);
  const [promptGroup, setPromptGroup] = useState<AgentGroup | null>(null);
  /** 그룹 검색어(이름·리더·멤버 이름에서 찾는다). */
  const [query, setQuery] = useState('');
  /** 마우스를 올린 그룹(정보 창에 쓸 좌표와 함께). */
  const [hover, setHover] = useState<{ group: AgentGroup; rect: DOMRect } | null>(null);
  const hoverTimer = useRef<number | null>(null);

  // 화면이 사라질 때 예약된 타이머를 정리한다.
  useEffect(
    () => () => {
      if (hoverTimer.current !== null) window.clearTimeout(hoverTimer.current);
    },
    [],
  );

  /** 살짝 지나갈 때 깜빡이지 않게 잠깐 기다렸다 띄운다(에이전트 카드와 같다). */
  const showHover = (group: AgentGroup, element: HTMLElement) => {
    if (hoverTimer.current !== null) window.clearTimeout(hoverTimer.current);
    hoverTimer.current = window.setTimeout(() => {
      setHover({ group, rect: element.getBoundingClientRect() });
    }, HOVER_DELAY_MS);
  };

  /** 바로 닫지 않는다 — 정보 창으로 마우스를 옮기는 동안(250ms) 살아 있게. */
  const hideHover = () => {
    if (hoverTimer.current !== null) window.clearTimeout(hoverTimer.current);
    hoverTimer.current = window.setTimeout(() => setHover(null), HOVER_DELAY_MS);
  };

  /** 정보 창에 마우스가 들어오면 닫힘 예약을 취소한다. */
  const keepHover = () => {
    if (hoverTimer.current !== null) window.clearTimeout(hoverTimer.current);
    hoverTimer.current = null;
  };

  const projectGroups = groups.filter((g) => g.projectId === project.id);
  const agentName = (id: number) => agents.find((a) => a.id === id)?.name ?? '?';

  const needle = query.trim().toLowerCase();
  const visibleGroups =
    needle === ''
      ? projectGroups
      : projectGroups.filter((group) =>
          [
            group.name,
            group.leaderAgentId === null ? '' : agentName(group.leaderAgentId),
            ...group.memberIds.map((agentId) => agentName(agentId)),
          ]
            .join(' ')
            .toLowerCase()
            .includes(needle),
        );

  const onDragOver = (e: DragEvent, groupId: number) => {
    if (!isAgentDrag(e)) return;
    e.preventDefault();
    setOverId(groupId);
  };

  const onDrop = (e: DragEvent, groupId: number) => {
    setOverId(null);
    dropOnGroup(e, groupId);
  };

  const onDelete = async (id: number, groupName: string) => {
    const ok = await askConfirm(`"${groupName}" 그룹을 삭제할까요? 에이전트는 삭제되지 않습니다.`, '삭제');
    if (!ok) return;
    deleteGroup(id);
  };

  return (
    <aside className={`${styles.panel} ${open ? '' : styles.panelCollapsed}`}>
      <div className={styles.list}>
      {/* 상자 안 상단 중앙의 얇은 화살표 손잡이 */}
      <button
        type="button"
        className={styles.tab}
        onClick={onToggle}
        aria-expanded={open}
        aria-label={open ? '그룹 패널 접기' : '그룹 패널 펴기'}
        title={open ? '그룹 패널 접기' : '그룹 패널 펴기'}
      >
        {open ? <ChevronUpIcon /> : <ChevronDownIcon />}
      </button>

      <div
        className={styles.header}
        role="button"
        tabIndex={0}
        aria-expanded={open}
        aria-label={open ? '그룹 패널 접기' : '그룹 패널 펴기'}
        onClick={onToggle}
        onKeyDown={(e) => {
          if (e.key !== 'Enter' && e.key !== ' ') return;
          e.preventDefault();
          onToggle();
        }}
      >
        <div className={styles.titleGroup}>
          <h2 className={styles.title}>
            그룹 <span className={shared.muted}>{projectGroups.length}</span>
          </h2>
        </div>
        <button
          type="button"
          className={shared.addButton}
          onClick={(e) => {
            e.stopPropagation();
            setCreating(true);
          }}
          aria-label="새 그룹"
          title="새 그룹"
        >
          <PlusIcon />
        </button>
      </div>

      {/* 그룹 검색 — 이름·리더·멤버 이름에서 찾는다(목록과 같은 폭). */}
      {open && (
        <div className={styles.filters} onClick={(e) => e.stopPropagation()}>
          <div className={styles.searchWrap}>
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="그룹·리더·멤버 검색"
              aria-label="그룹 검색"
            />
          </div>
        </div>
      )}

      <div className={styles.body}>
        <div className={styles.cards}>
          {projectGroups.length === 0 && (
            <p className={shared.muted}>그룹이 없습니다. 에이전트는 그룹 없이도 쓸 수 있습니다.</p>
          )}
          {visibleGroups.map((group) => {
            const selected = target?.kind === 'group' && target.id === group.id;
            return (
              <div
                key={group.id}
                className={`${styles.card} ${selected ? styles.cardSelected : ''} ${overId === group.id ? styles.cardOver : ''}`}
                onClick={() => onSelect({ kind: 'group', id: group.id })}
                onMouseEnter={(e) => showHover(group, e.currentTarget)}
                onMouseLeave={hideHover}
                onDragOver={(e) => onDragOver(e, group.id)}
                onDragLeave={() => setOverId(null)}
                onDrop={(e) => onDrop(e, group.id)}
                title="누르면 채팅 대상으로 선택됩니다"
              >
                <div className={styles.cardTop}>
                  <strong className={styles.name}>{group.name}</strong>
                  <button
                    type="button"
                    className={styles.menuButton}
                    aria-label={`${group.name} 메뉴`}
                    aria-haspopup="menu"
                    onClick={(e) => {
                      e.stopPropagation();
                      setMenu({ group, rect: e.currentTarget.getBoundingClientRect() });
                    }}
                  >
                    ⋮
                  </button>
                </div>
                <div className={shared.muted}>
                  멤버 {group.memberIds.length} · 리더{' '}
                  {group.leaderAgentId !== null ? agentName(group.leaderAgentId) : '없음'}
                </div>
                <div className={styles.members}>
                  {group.memberIds.length === 0 && (
                    <span className={styles.emptyDrop}>에이전트를 여기로 끌어 놓으세요</span>
                  )}
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
      </div>
      </div>

      {hover && (
        <GroupHoverCard
          group={hover.group}
          anchor={hover.rect}
          onKeepOpen={keepHover}
          onLeave={hideHover}
        />
      )}

      {creating && <GroupFormModal project={project} onClose={() => setCreating(false)} />}

      {menu && (
        <GroupCardMenu
          anchor={menu.rect}
          onClose={() => setMenu(null)}
          onEditPrompt={() => {
            setPromptGroup(menu.group);
            setMenu(null);
          }}
          onDelete={() => {
            const target = menu.group;
            setMenu(null);
            onDelete(target.id, target.name);
          }}
        />
      )}
      {promptGroup && <GroupPromptModal group={promptGroup} onClose={() => setPromptGroup(null)} />}
    </aside>
  );
}
