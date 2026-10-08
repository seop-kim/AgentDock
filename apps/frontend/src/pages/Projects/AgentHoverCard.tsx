import { createPortal } from 'react-dom';
import { unavailableReason } from '../../lib/agentAvailability';
import { useAgentDockStore } from '../../store/AgentDockStore';
import shared from '../../styles/shared.module.css';
import type { Agent, Project } from '../../types';
import styles from './AgentHoverCard.module.css';

const CARD_WIDTH = 320;
const ESTIMATED_HEIGHT = 300;

/**
 * 에이전트 카드에 마우스를 올리면 카드 오른쪽에 뜨는 정보 창: 이름, 역할, 소속 그룹, 연결된 에이전트, 프롬프트.
 * "연결된 에이전트"는 같은 그룹에 속한 다른 에이전트다(리더는 ★).
 * 마우스를 이 창으로 옮겨도 유지되고(옮기는 동안 꺼지지 않게), 길면 안에서 스크롤해 읽을 수 있다.
 */
export default function AgentHoverCard({
  agent,
  project,
  anchor,
  onKeepOpen,
  onLeave,
}: {
  agent: Agent;
  project: Project;
  /** 카드의 화면 좌표 */
  anchor: DOMRect;
  /** 정보 창으로 마우스가 들어오면 닫힘 예약을 취소한다(옮겨 가는 동안 꺼지지 않게). */
  onKeepOpen?: () => void;
  /** 정보 창에서 마우스가 나가면 닫힘 예약을 건다. */
  onLeave?: () => void;
}) {
  const { agents, groups, providers, roles } = useAgentDockStore();
  const role = roles.find((r) => r.id === agent.roleId);
  const provider = providers.find((p) => p.id === agent.providerId);
  const reason = unavailableReason(agent, providers, project);
  const myGroups = groups.filter((g) => g.projectId === project.id && g.memberIds.includes(agent.id));

  const linked = myGroups.flatMap((g) =>
    g.memberIds
      .filter((id) => id !== agent.id)
      .map((id) => ({ id, name: agents.find((a) => a.id === id)?.name ?? '?', isLeader: g.leaderAgentId === id })),
  );
  const uniqueLinked = linked.filter((l, i) => linked.findIndex((x) => x.id === l.id) === i);

  // 화면 밖으로 나가지 않게: 오른쪽에 자리가 없으면 카드 왼쪽에 붙이고, 위아래도 안쪽으로 당긴다.
  const roomRight = window.innerWidth - anchor.right - 12;
  const left = roomRight >= CARD_WIDTH ? anchor.right + 12 : Math.max(8, anchor.left - CARD_WIDTH - 12);
  const top = Math.max(8, Math.min(anchor.top, window.innerHeight - ESTIMATED_HEIGHT));

  return createPortal(
    <div
      className={styles.card}
      role="tooltip"
      onMouseEnter={onKeepOpen}
      onMouseLeave={onLeave}
      style={{ '--top': `${top}px`, '--left': `${left}px` } as React.CSSProperties}
    >
      <div className={styles.head}>
        <strong className={styles.name}>{agent.name}</strong>
        <span className={styles.role}>{role?.name}</span>
        {agent.id === project.masterAgentId && <span className={styles.master}>프로젝트 마스터</span>}
      </div>
      {reason && <p className={styles.warn}>지금은 실행할 수 없습니다: {reason}</p>}

      <dl className={styles.rows}>
        <dt>런타임</dt>
        <dd>
          {provider?.name ?? '삭제된 런타임'} · {agent.model || 'CLI 기본 모델'} / {agent.mode || 'CLI 기본 모드'}
        </dd>
        <dt>소속 그룹</dt>
        <dd>
          {myGroups.length === 0 ? (
            <span className={shared.muted}>없음</span>
          ) : (
            myGroups.map((g) => g.name).join(', ')
          )}
        </dd>
        <dt>연결된 에이전트</dt>
        <dd>
          {uniqueLinked.length === 0 ? (
            <span className={shared.muted}>없음</span>
          ) : (
            <span className={styles.chips}>
              {uniqueLinked.map((l) => (
                <span key={l.id} className={styles.chip}>
                  {l.isLeader && '★ '}
                  {l.name}
                </span>
              ))}
            </span>
          )}
        </dd>
      </dl>

      <div className={styles.promptLabel}>프롬프트</div>
      <dl className={styles.rows}>
        <dt>에이전트</dt>
        <dd>{agent.persona || <span className={shared.muted}>없음</span>}</dd>
      </dl>
    </div>,
    document.body,
  );
}
