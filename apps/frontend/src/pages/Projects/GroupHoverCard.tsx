import { createPortal } from 'react-dom';
import shared from '../../styles/shared.module.css';
import type { AgentGroup } from '../../types';
import styles from './AgentHoverCard.module.css';

const CARD_WIDTH = 320;
/** 정보 창의 고정 높이(CSS 의 .cardShort height 와 맞춘다) — 길어도 이 크기를 넘지 않는다. */
const CARD_HEIGHT = 280;

/**
 * 그룹 카드에 마우스를 올리면 카드 오른쪽에 뜨는 정보 창.
 *
 * <p>여기서는 **그룹 프롬프트만** 보여 준다 — 에이전트 카드처럼 마스터·에이전트 프롬프트까지 겹쳐 보이면
 * 정작 그 그룹의 규칙을 찾기 어렵다. (공유 노트는 프롬프트 편집 창에서 본다.)
 * 마우스 조작을 막지 않도록 클릭은 통과시킨다(에이전트 호버 카드와 같은 스타일을 쓴다).
 */
export default function GroupHoverCard({
  group,
  anchor,
  onKeepOpen,
  onLeave,
}: {
  group: AgentGroup;
  anchor: DOMRect;
  /** 정보 창으로 마우스가 들어오면 닫힘 예약을 취소한다(옮겨 가는 동안 꺼지지 않게). */
  onKeepOpen?: () => void;
  /** 정보 창에서 마우스가 나가면 닫힘 예약을 건다. */
  onLeave?: () => void;
}) {
  // 화면 밖으로 나가지 않게: 오른쪽에 자리가 없으면 카드 왼쪽에, 아래에 자리가 없으면 **위로 펼친다**.
  const roomRight = window.innerWidth - anchor.right - 12;
  const left = roomRight >= CARD_WIDTH ? anchor.right + 12 : Math.max(8, anchor.left - CARD_WIDTH - 12);
  const fitsBelow = anchor.top + CARD_HEIGHT + 8 <= window.innerHeight;
  const top = fitsBelow ? anchor.top : Math.max(8, anchor.bottom - CARD_HEIGHT);

  return createPortal(
    <div
      className={`${styles.card} ${styles.cardShort}`}
      role="tooltip"
      onMouseEnter={onKeepOpen}
      onMouseLeave={onLeave}
      style={{ '--top': `${top}px`, '--left': `${left}px` } as React.CSSProperties}
    >
      <div className={styles.head}>
        <strong className={styles.name}>{group.name}</strong>
      </div>
      <div className={styles.promptLabel}>그룹 프롬프트</div>
      <div className={styles.promptBody}>
        <dl className={styles.rows}>
          <dt>내용</dt>
          <dd>{group.prompt || <span className={shared.muted}>비어 있음</span>}</dd>
        </dl>
      </div>
    </div>,
    document.body,
  );
}
