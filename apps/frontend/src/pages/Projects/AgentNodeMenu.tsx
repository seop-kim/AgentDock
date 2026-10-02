import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import menu from '../../styles/menu.module.css';

const MENU_WIDTH = 168;

/**
 * 구성도 에이전트 노드의 "⋮" 메뉴. 노드 위에 버튼(리더 지정·그룹에서 제거·구성도에서 빼기)을 늘어놓지 않고
 * 여기에 모아 둔다. 캔버스의 확대/축소·이동에 잘리지 않도록 body 에 포털로 그린다.
 *
 * <p>**마스터 노드도 이 메뉴를 갖는다**(`isMaster`) — 다만 마스터는 구성도에서 뺄 수 없고 삭제도 안 되며
 * 그룹에도 속하지 않으므로 **비파괴 동작(터미널 보기)만** 남긴다.
 */
export default function AgentNodeMenu({
  anchor,
  inGroup,
  isLeader,
  isMaster,
  onClose,
  onSetLeader,
  onRemoveFromGroup,
  onRemoveFromCanvas,
  onTerminal,
}: {
  /** "⋮" 버튼의 화면 좌표 */
  anchor: DOMRect;
  /** 그룹에 속해 있는지(속하지 않으면 그룹 관련 항목은 숨긴다) */
  inGroup: boolean;
  isLeader: boolean;
  /** 프로젝트 마스터인지. 마스터는 구성도에서 빼기·그룹 관련 항목을 두지 않는다(불변). */
  isMaster: boolean;
  onClose: () => void;
  onSetLeader: () => void;
  onRemoveFromGroup: () => void;
  onRemoveFromCanvas: () => void;
  /** 이 에이전트가 돌리는 터미널(실행 출력) 창을 연다 */
  onTerminal: () => void;
}) {
  useEffect(() => {
    const onPointerDown = (e: PointerEvent) => {
      if (!(e.target as HTMLElement).closest('[data-node-menu]')) onClose();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKey);
    window.addEventListener('scroll', onClose, true);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKey);
      window.removeEventListener('scroll', onClose, true);
    };
  }, [onClose]);

  const left = Math.max(8, Math.min(anchor.right - MENU_WIDTH, window.innerWidth - MENU_WIDTH - 8));

  return createPortal(
    <div
      className={menu.menu}
      data-node-menu
      role="menu"
      style={{ '--top': `${anchor.bottom + 4}px`, '--left': `${left}px` } as React.CSSProperties}
    >
      <button type="button" role="menuitem" className={menu.item} onClick={onTerminal}>
        터미널 보기
      </button>
      {/* 마스터는 그룹에 속하지 않고 구성도에서 뺄 수도 없다(불변) — 파괴적인 항목은 두지 않는다. */}
      {!isMaster && inGroup && !isLeader && (
        <button type="button" role="menuitem" className={menu.item} onClick={onSetLeader}>
          리더로 지정
        </button>
      )}
      {!isMaster && inGroup && (
        <button type="button" role="menuitem" className={menu.item} onClick={onRemoveFromGroup}>
          그룹에서 제거
        </button>
      )}
      {!isMaster && (
        <button
          type="button"
          role="menuitem"
          className={`${menu.item} ${menu.danger}`}
          onClick={onRemoveFromCanvas}
        >
          구성도에서 빼기
        </button>
      )}
    </div>,
    document.body,
  );
}
