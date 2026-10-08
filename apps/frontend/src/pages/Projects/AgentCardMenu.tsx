import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { api } from '../../lib/api';
import { askConfirm } from '../../components/ConfirmDialog';
import { isLive } from '../../lib/executions';
import { useAgentDockStore } from '../../store/AgentDockStore';
import menu from '../../styles/menu.module.css';
import type { Execution } from '../../types';

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
  onTerminal,
  /** 이 에이전트가 지금 돌리는 실행(있으면 "작업 강제 종료" 항목이 생긴다). */
  running,
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
  /** 이 에이전트가 돌리는 터미널(실행 출력) 창을 연다 */
  onTerminal: () => void;
  /** 지금 돌고 있는 실행(없으면 null/undefined). 강제 종료 항목을 띄울지 판단한다. */
  running?: Execution | null;
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

  const { reload } = useAgentDockStore();
  const [stopping, setStopping] = useState(false);

  /**
   * 이 에이전트가 지금 돌리는 실행을 **강제 종료**한다.
   * 취소는 자손까지 전파되므로, 그 실행이 위임해 둔 자식 실행도 함께 멈춘다.
   */
  const forceStop = async (execution: Execution) => {
    const ok = await askConfirm(
      `실행 #${execution.id} 을(를) 강제 종료할까요? 그 아래 위임된 자식 실행도 함께 멈춥니다.`,
      '강제 종료',
    );
    if (!ok) return;
    setStopping(true);
    void api
      .cancelExecution(execution.id)
      .then(() => reload())
      .finally(() => {
        setStopping(false);
        onClose();
      });
  };

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
      <button type="button" role="menuitem" className={menu.item} onClick={onTerminal}>
        터미널 보기
      </button>
      {running != null && isLive(running.status) && (
        <button
          type="button"
          role="menuitem"
          className={`${menu.item} ${menu.danger}`}
          disabled={stopping}
          onClick={() => forceStop(running)}
          title="이 에이전트가 돌리는 실행을 지금 멈춥니다(위임한 자식도 함께)"
        >
          {stopping ? '종료 중…' : '작업 강제 종료'}
        </button>
      )}
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
