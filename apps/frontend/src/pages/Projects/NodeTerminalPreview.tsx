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
 * 크기는 고정이지만 **안에서 스크롤**해 긴 로그도 읽을 수 있고, 마우스가 미리보기 위에 있는 동안은 닫히지 않는다
 * (노드와 미리보기 **둘 다**에서 마우스가 벗어나야 닫힌다 — 여닫는 판단은 부모 `GroupCanvas` 가 한다).
 * 캔버스의 확대/축소·이동에 잘리지 않도록 body 에 포털로 그린다. 화면 밖으로 넘치면 안쪽으로 밀어 넣는다.
 */
export default function NodeTerminalPreview({
  executionId,
  live,
  agentName,
  anchor,
  onMouseEnter,
  onMouseLeave,
}: {
  executionId: number;
  /** 그 실행이 아직 도는 중인지(줄 끝 커서를 깜빡일지). */
  live: boolean;
  agentName: string;
  /** 노드의 화면 좌표. 미리보기를 그 아래에 붙인다. */
  anchor: DOMRect;
  /** 미리보기 위로 마우스가 들어오면 닫힘 예약을 취소한다. */
  onMouseEnter: () => void;
  /** 미리보기에서 마우스가 벗어나면 닫힘을 예약한다. */
  onMouseLeave: () => void;
}) {
  const left = clamp(anchor.left, MARGIN, Math.max(MARGIN, window.innerWidth - WIDTH - MARGIN));
  const top = clamp(anchor.bottom + 8, MARGIN, Math.max(MARGIN, window.innerHeight - HEIGHT - MARGIN));

  return createPortal(
    <div
      className={styles.nodePreview}
      data-node-terminal-preview
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
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
      <TerminalView executionId={executionId} live={live} className={styles.nodePreviewBody} trackTail />
    </div>,
    document.body,
  );
}
