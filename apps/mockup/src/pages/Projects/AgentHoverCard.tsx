import { createPortal } from 'react-dom';
import { unavailableReason } from '../../lib/agentAvailability';
import { useMockStore } from '../../store/MockStore';
import { SEED_ROLES } from '../../store/seed';
import shared from '../../styles/shared.module.css';
import type { Agent, Project } from '../../types';
import styles from './AgentHoverCard.module.css';

const CARD_WIDTH = 320;
const ESTIMATED_HEIGHT = 300;

/**
 * 에이전트 카드에 마우스를 올리면 카드 오른쪽에 뜨는 정보 창: 이름, 역할, 소속 그룹, 연결된 에이전트, 프롬프트.
 * "연결된 에이전트"는 같은 그룹에 속한 다른 에이전트다(리더는 ★). 마우스 조작을 막지 않도록 클릭은 통과시킨다.
 */
export default function AgentHoverCard({
  agent,
  project,
  anchor,
}: {
  agent: Agent;
  project: Project;
  /** 카드의 화면 좌표 */
  anchor: DOMRect;
}) {
  const { agents, groups, providers } = useMockStore();
  const role = SEED_ROLES.find((r) => r.id === agent.roleId);
  const provider = providers.find((p) => p.id === agent.providerId);
  const reason = unavailableReason(agent, providers, project);
  const myGroups = groups.filter((g) => g.projectId === project.id && g.memberIds.includes(agent.id));

  const linked = myGroups.flatMap((g) =>
    g.memberIds
      .filter((id) => id !== agent.id)
      .map((id) => ({ id, name: agents.find((a) => a.id === id)?.name ?? '?', isLeader: g.leaderAgentId === id })),
  );
  const uniqueLinked = linked.filter((l, i) => linked.findIndex((x) => x.id === l.id) === i);

  const left = Math.min(anchor.right + 12, window.innerWidth - CARD_WIDTH - 8);
  const top = Math.max(8, Math.min(anchor.top, window.innerHeight - ESTIMATED_HEIGHT));

  return createPortal(
    <div
      className={styles.card}
      role="tooltip"
      style={{ '--top': `${top}px`, '--left': `${left}px` } as React.CSSProperties}
    >
      <div className={styles.head}>
        <strong className={styles.name}>{agent.name}</strong>
        <span className={styles.role}>{role?.name}</span>
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
      <p className={agent.persona ? styles.prompt : `${styles.prompt} ${styles.promptEmpty}`}>
        {agent.persona || '설정된 프롬프트가 없습니다.'}
      </p>
    </div>,
    document.body,
  );
}
