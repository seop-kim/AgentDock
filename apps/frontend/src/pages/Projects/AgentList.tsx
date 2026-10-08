import { DragEvent, useEffect, useRef, useState } from 'react';
import { unavailableReason } from '../../lib/agentAvailability';
import { agentStatus } from '../../lib/agentStatus';
import { isAgentDrag, readAgentDrag, startAgentDrag } from '../../lib/dnd';
import { isLive, waitingInputs } from '../../lib/executions';
import { openTerminalWindow } from '../../lib/windowSync';
import { ChevronDownIcon, ChevronUpIcon, PlusIcon } from '../../components/icons';
import { useAgentDockStore } from '../../store/AgentDockStore';
import shared from '../../styles/shared.module.css';
import type { Agent, AgentGroup, AgentRole, Execution, Project, Task } from '../../types';
import AgentCardMenu from './AgentCardMenu';
import AgentFormModal from './AgentFormModal';
import AgentHoverCard from './AgentHoverCard';
import WaitingInputPopup from './WaitingInputPopup';
import styles from './AgentList.module.css';
import waiting from './WaitingInput.module.css';

/** 마우스를 올린 뒤 정보 창이 뜨기까지의 지연(스쳐 지나갈 때 깜빡이지 않게) */
const HOVER_DELAY_MS = 250;

/** 에이전트 목록 필터. 셋을 **함께**(AND) 만족해야 보인다 — 복합 조건. */
interface AgentFilters {
  /** 이름·역할·모델에서 찾는 검색어 */
  query: string;
  status: 'all' | 'WORKING' | 'UNPLACED' | 'IDLE' | 'WAITING';
  groupId: number | 'all';
}

const NO_FILTERS: AgentFilters = { query: '', status: 'all', groupId: 'all' };

/** 이 에이전트가 지금 조건에 맞는지(검색어 AND 상태 AND 그룹). */
function matchesFilters(
  agent: Agent,
  filters: AgentFilters,
  groups: AgentGroup[],
  roles: AgentRole[],
  tasks: Task[],
  executions: Execution[],
): boolean {
  const needle = filters.query.trim().toLowerCase();
  if (needle !== '') {
    const roleName = roles.find((role) => role.id === agent.roleId)?.name ?? '';
    const haystack = `${agent.name} ${roleName} ${agent.model ?? ''}`.toLowerCase();
    if (!haystack.includes(needle)) return false;
  }
  if (filters.status !== 'all' && agentStatus(agent, tasks, executions).kind !== filters.status) {
    return false;
  }
  if (filters.groupId !== 'all') {
    const group = groups.find((candidate) => candidate.id === filters.groupId);
    const memberIds = group === undefined ? [] : group.memberIds;
    if (!memberIds.includes(agent.id) && group?.leaderAgentId !== agent.id) return false;
  }
  return true;
}

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
  focusSeq,
  onSelectAgent,
  open,
  onToggle,
}: {
  project: Project;
  selectedAgentId: number | null;
  /** 값이 바뀔 때마다 선택된 에이전트의 카드를 보이는 자리로 스크롤한다(구성도에서 노드를 눌렀을 때). */
  focusSeq: number;
  onSelectAgent: (agentId: number) => void;
  /** 패널을 펼쳐 둘지(접으면 머리말만 남는다) */
  open: boolean;
  onToggle: () => void;
}) {
  const { agents, groups, providers, tasks, executions, roles, removeGroupMember, deleteAgent, setAgentPlaced } =
    useAgentDockStore();
  /** 목록 필터(검색어·상태·그룹) — 복합으로 걸린다. */
  const [filters, setFilters] = useState<AgentFilters>(NO_FILTERS);
  const filtering =
    filters.query.trim() !== '' || filters.status !== 'all' || filters.groupId !== 'all';
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Agent | null>(null);
  const [dropActive, setDropActive] = useState(false);
  const [hover, setHover] = useState<{ agent: Agent; rect: DOMRect } | null>(null);
  const [menu, setMenu] = useState<{ agent: Agent; rect: DOMRect } | null>(null);
  // 카드의 말풍선(💬)을 누르면 그 자리에 뜨는 입력 대기 팝업.
  const [waitingPopup, setWaitingPopup] = useState<{ execution: Execution; rect: DOMRect } | null>(null);
  const hoverTimer = useRef<number | undefined>(undefined);
  /** 카드 DOM. 구성도에서 노드를 누르면 그 카드로 스크롤하려고 들고 있는다. */
  const cardRefs = useRef(new Map<number, HTMLDivElement>());

  const projectAgents = agents.filter((a) => a.projectId === project.id);
  // 에이전트마다 답을 기다리는 첫 실행(부모·자식 모두 포함). 그 에이전트 카드에 말풍선을 붙인다.
  const waitingByAgent = new Map<number, Execution>();
  waitingInputs(executions.filter((execution) => execution.projectId === project.id)).forEach((execution) => {
    if (!waitingByAgent.has(execution.agentId)) waitingByAgent.set(execution.agentId, execution);
  });

  useEffect(() => () => window.clearTimeout(hoverTimer.current), []);

  // 구성도에서 노드를 눌렀을 때(반대 방향): 그 에이전트 카드를 보이는 자리로 스크롤한다.
  // 카드 강조는 선택 상태(selectedAgentId)가 이미 하므로 여기서는 스크롤만 맡는다.
  useEffect(() => {
    if (focusSeq === 0 || selectedAgentId === null) return;
    cardRefs.current.get(selectedAgentId)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusSeq]);

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
      className={`${styles.panel} ${open ? '' : styles.panelCollapsed}`}
      onDragOver={onDragOver}
      onDragLeave={() => setDropActive(false)}
      onDrop={onDrop}
    >
      <div className={`${styles.list} ${dropActive ? styles.dropActive : ''}`}>
      {/* 상자 안 상단 중앙의 얇은 화살표 손잡이 */}
      <button
        type="button"
        className={styles.tab}
        onClick={onToggle}
        aria-expanded={open}
        aria-label={open ? '에이전트 패널 접기' : '에이전트 패널 펴기'}
        title={open ? '에이전트 패널 접기' : '에이전트 패널 펴기'}
      >
        {open ? <ChevronUpIcon /> : <ChevronDownIcon />}
      </button>

      <div
        className={styles.header}
        role="button"
        tabIndex={0}
        aria-expanded={open}
        aria-label={open ? '에이전트 패널 접기' : '에이전트 패널 펴기'}
        onClick={onToggle}
        onKeyDown={(e) => {
          if (e.key !== 'Enter' && e.key !== ' ') return;
          e.preventDefault();
          onToggle();
        }}
      >
        <div className={styles.titleGroup}>
          <h2 className={styles.title}>
            에이전트 <span className={shared.muted}>{projectAgents.length}</span>
          </h2>
        </div>
        <button
          type="button"
          className={shared.addButton}
          onClick={(e) => {
            e.stopPropagation();
            setCreating(true);
          }}
          aria-label="새 에이전트"
          title="새 에이전트"
        >
          <PlusIcon />
        </button>
      </div>

      {/* 검색 + 상태/그룹 필터 — 셋을 함께(AND) 걸 수 있다. */}
      {open && (
        <div className={styles.filters} onClick={(e) => e.stopPropagation()}>
          <input
            type="search"
            value={filters.query}
            onChange={(e) => setFilters((prev) => ({ ...prev, query: e.target.value }))}
            placeholder="이름·역할·모델 검색"
            aria-label="에이전트 검색"
          />
          <select
            value={filters.status}
            onChange={(e) => setFilters((prev) => ({ ...prev, status: e.target.value as AgentFilters['status'] }))}
            aria-label="상태 필터"
            title="상태로 거르기"
          >
            <option value="all">상태 전체</option>
            <option value="WORKING">작업 중만</option>
            <option value="WAITING">작업 대기중</option>
            <option value="IDLE">작업 없음</option>
            <option value="UNPLACED">미배치</option>
          </select>
          <select
            value={filters.groupId === 'all' ? 'all' : String(filters.groupId)}
            onChange={(e) =>
              setFilters((prev) => ({
                ...prev,
                groupId: e.target.value === 'all' ? 'all' : Number(e.target.value),
              }))
            }
            aria-label="그룹 필터"
            title="그룹으로 거르기"
          >
            <option value="all">그룹 전체</option>
            {groups
              .filter((group) => group.projectId === project.id)
              .map((group) => (
                <option key={group.id} value={group.id}>
                  {group.name}
                </option>
              ))}
          </select>
          <span className={styles.filterCount}>
            {projectAgents.filter((agent) => matchesFilters(agent, filters, groups, roles, tasks, executions)).length}{' '}
            / {projectAgents.length}
          </span>
          {filtering && (
            <button type="button" className={styles.filterReset} onClick={() => setFilters(NO_FILTERS)}>
              초기화
            </button>
          )}
        </div>
      )}

      <div className={styles.body}>
        <div className={styles.cards}>
          {projectAgents.length === 0 && (
            <p className={shared.muted}>에이전트가 없습니다. + 버튼으로 만들어 보세요.</p>
          )}
          {projectAgents.length > 0 && filtering && (
            <p className={shared.muted}>조건에 맞는 에이전트가 없습니다 — 필터를 풀어 보세요.</p>
          )}
          {projectAgents
            .filter((agent) => matchesFilters(agent, filters, groups, roles, tasks, executions))
            .map((agent) => {
            const role = roles.find((r) => r.id === agent.roleId);
            const reason = unavailableReason(agent, providers, project);
            const status = agentStatus(agent, tasks, executions);
            const statusClass = {
              UNPLACED: styles.statusUnplaced,
              WORKING: styles.statusWorking,
              WAITING: styles.statusWaiting,
              IDLE: styles.statusIdle,
            }[status.kind];
            const selected = selectedAgentId === agent.id;
            const pending = waitingByAgent.get(agent.id) ?? null;
            // 지금 돌고 있는 실행(있으면 상태 배지가 터미널 열기 버튼이 된다).
            const running =
              executions.find((execution) => execution.agentId === agent.id && isLive(execution.status)) ?? null;
            return (
              <div
                key={agent.id}
                ref={(el) => {
                  if (el) cardRefs.current.set(agent.id, el);
                  else cardRefs.current.delete(agent.id);
                }}
                data-agent-id={agent.id}
                data-agent-name={agent.name}
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
                <div className={styles.text}>
                  <strong className={styles.name}>{agent.name}</strong>
                  <span className={styles.role}>{role?.name}</span>
                </div>
                {project.masterAgentId === agent.id && <span className={styles.master}>마스터</span>}
                {/* 입력 대기 말풍선: 누르면 카드에 붙은 팝업에서 질문에 답한다(선택/끌기로 번지지 않게 막는다). */}
                {pending !== null && (
                  <button
                    type="button"
                    className={waiting.bubbleOnCard}
                    aria-label={`${agent.name} 입력 대기 질문 열기`}
                    title="입력 대기 중인 질문이 있습니다"
                    onClick={(e) => {
                      e.stopPropagation();
                      hideHover();
                      setWaitingPopup({ execution: pending, rect: e.currentTarget.getBoundingClientRect() });
                    }}
                  >
                    💬
                  </button>
                )}
                {running ? (
                  // 작업 중 상태 배지는 누를 수 있다: 그 에이전트의 터미널 창을 연다(카드 선택·끌기로 번지지 않게 막는다).
                  <button
                    type="button"
                    className={`${styles.status} ${statusClass} ${styles.statusButton}`}
                    aria-label={`${agent.name} 터미널 열기`}
                    title={`${status.label} · 눌러서 터미널 열기`}
                    onClick={(e) => {
                      e.stopPropagation();
                      hideHover();
                      openTerminalWindow(agent.id);
                    }}
                  >
                    {status.label}
                  </button>
                ) : (
                  <span className={`${styles.status} ${statusClass}`} title={`상태: ${status.label}`}>
                    {status.label}
                  </span>
                )}
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
                  ⋮
                </button>
              </div>
            );
          })}
        </div>
      </div>
      </div>

      {hover && <AgentHoverCard agent={hover.agent} project={project} anchor={hover.rect} />}
      {menu && (
        <AgentCardMenu
          anchor={menu.rect}
          placed={menu.agent.placed}
          isMaster={project.masterAgentId === menu.agent.id}
          running={executions.find((execution) => execution.agentId === menu.agent.id && isLive(execution.status)) ?? null}
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
          onTerminal={() => {
            openTerminalWindow(menu.agent.id);
            setMenu(null);
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
      {waitingPopup && (
        <WaitingInputPopup
          execution={waitingPopup.execution}
          anchor={waitingPopup.rect}
          onClose={() => setWaitingPopup(null)}
        />
      )}
    </aside>
  );
}
