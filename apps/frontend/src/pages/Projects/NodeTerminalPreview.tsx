import { CSSProperties } from 'react';
import { createPortal } from 'react-dom';
import TerminalView from '../../components/TerminalView';
import styles from './GroupCanvas.module.css';

const WIDTH = 420;
const HEIGHT = 180;
const MARGIN = 8;

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), Math.max(min, max));

/**
 * 작업 중인 에이전트 노드에 마우스를 올렸을 때 뜨는 **작은 터미널 미리보기**.
 *
 * <p>그 에이전트의 지금 실행이 흘려보낸 최근 줄을 `TerminalView`(실행 SSE 스트림)로 그대로 받아 보여 준다.
 * 크기는 고정이고, `pointer-events: none` 이라 **클릭·끌기·선택을 절대 가로채지 않는다**(노드는 늘 누를 수 있다).
 * 캔버스의 확대/축소·이동에 잘리지 않도록 body 에 포털로 그린다. 뜨고 사라지는 것은 노드의 hover 가 정한다.
 */
export default function NodeTerminalPreview({
  executionId,
  live,
  agentName,
  anchor,
}: {
  executionId: number;
  /** 그 실행이 아직 도는 중인지(줄 끝 커서를 깜빡일지). */
  live: boolean;
  agentName: string;
  /** 노드의 화면 좌표. 미리보기를 그 아래에 붙인다. */
  anchor: DOMRect;
}) {
  const left = clamp(anchor.left, MARGIN, Math.max(MARGIN, window.innerWidth - WIDTH - MARGIN));
  const top = clamp(anchor.bottom + 8, MARGIN, Math.max(MARGIN, window.innerHeight - HEIGHT - MARGIN));

  return createPortal(
    <div
      className={styles.nodePreview}
      data-node-terminal-preview
      aria-hidden="true"
      style={
        {
          '--top': `${top}px`,
          '--left': `${left}px`,
          '--preview-w': `${WIDTH}px`,
          '--preview-h': `${HEIGHT}px`,
        } as CSSProperties
      }
    >
      <span className={styles.nodePreviewTitle}>{agentName} 실행 출력</span>
      <TerminalView executionId={executionId} live={live} className={styles.nodePreviewBody} />
    </div>,
    document.body,
  );
}
