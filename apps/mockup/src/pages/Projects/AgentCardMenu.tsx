import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import menu from '../../styles/menu.module.css';

const MENU_WIDTH = 168;

/**
 * 에이전트 카드의 "…" 메뉴. 패널의 스크롤/블러 영역에 잘리지 않도록 body 에 포털로 그린다.
 * 상세 설정 수정, 구성도에 놓기/빼기, 삭제를 한다. 바깥을 누르거나 Esc, 스크롤하면 닫힌다.
 */
export default function AgentCardMenu({
  anchor,
  placed,
  isMaster,
  onEdit,
  onTogglePlaced,
  onDelete,
  onClose,
}: {
  /** "…" 버튼의 화면 좌표 */
  anchor: DOMRect;
  /** 구성도에 놓여 있는지(메뉴 문구가 바뀐다) */
  placed: boolean;
  /** 프로젝트 마스터면 구성도에서 빼기·삭제를 숨긴다(최상위 리더라 빠질 수 없다) */
  isMaster: boolean;
  onEdit: () => void;
  /** 구성도에서 빼기 / 구성도에 놓기 */
  onTogglePlaced: () => void;
  onDelete: () => void;
  onClose: () => void;
}) {
  useEffect(() => {
    const onPointerDown = (e: PointerEvent) => {
      if (!(e.target as HTMLElement).closest('[data-agent-menu]')) onClose();
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
      data-agent-menu
      role="menu"
      style={{ '--top': `${anchor.bottom + 4}px`, '--left': `${left}px` } as React.CSSProperties}
    >
      <button type="button" role="menuitem" className={menu.item} onClick={onEdit}>
        상세 설정
      </button>
      {!isMaster && (
        <button type="button" role="menuitem" className={menu.item} onClick={onTogglePlaced}>
          {placed ? '구성도에서 빼기' : '구성도에 놓기'}
        </button>
      )}
      {!isMaster && (
        <button type="button" role="menuitem" className={`${menu.item} ${menu.danger}`} onClick={onDelete}>
          삭제
        </button>
      )}
    </div>,
    document.body,
  );
}
