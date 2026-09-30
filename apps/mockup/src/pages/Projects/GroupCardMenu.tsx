import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import menu from '../../styles/menu.module.css';

const MENU_WIDTH = 168;

/** 그룹 카드의 "⋮" 메뉴. 프롬프트 편집과 삭제. 카드 패널에 잘리지 않도록 body 에 포털로 그린다. */
export default function GroupCardMenu({
  anchor,
  onEditPrompt,
  onDelete,
  onClose,
}: {
  /** "⋮" 버튼의 화면 좌표 */
  anchor: DOMRect;
  onEditPrompt: () => void;
  onDelete: () => void;
  onClose: () => void;
}) {
  useEffect(() => {
    const onPointerDown = (e: PointerEvent) => {
      if (!(e.target as HTMLElement).closest('[data-group-menu]')) onClose();
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
      data-group-menu
      role="menu"
      style={{ '--top': `${anchor.bottom + 4}px`, '--left': `${left}px` } as React.CSSProperties}
    >
      <button type="button" role="menuitem" className={menu.item} onClick={onEditPrompt}>
        프롬프트 편집
      </button>
      <button type="button" role="menuitem" className={`${menu.item} ${menu.danger}`} onClick={onDelete}>
        삭제
      </button>
    </div>,
    document.body,
  );
}
