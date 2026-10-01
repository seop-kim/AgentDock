import { CSSProperties, FormEvent, useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { questionText } from '../../lib/executions';
import { useAgentDockStore } from '../../store/AgentDockStore';
import exec from '../../styles/execution.module.css';
import type { Execution } from '../../types';
import styles from './WaitingInput.module.css';

/** 캔버스 노드/에이전트 카드에 붙는 말풍선 팝업의 너비. */
const POPUP_WIDTH = 300;

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), Math.max(min, max));

/**
 * 입력 대기(계약 `ask`) 한 건: 물은 에이전트 이름 · 질문 · 보기(버튼) · 자유 입력.
 * 입력 대기 배너와 캔버스 말풍선 팝업이 **같은 모양**을 쓴다(한 곳에서만 고친다).
 * 답을 보내면 기존 스토어 동작(`answerExecution`)이 서버에 넣고 화면을 다시 읽어 이 항목이 사라진다.
 */
export function WaitingInputItem({ execution, agentName }: { execution: Execution; agentName: string }) {
  const { answerExecution } = useAgentDockStore();
  const [text, setText] = useState('');

  const send = (value: string) => {
    const answer = value.trim();
    if (answer === '') return;
    answerExecution(execution.id, answer);
    setText('');
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    send(text);
  };

  return (
    <div className={exec.question}>
      <p className={exec.questionText}>
        ⎿ {agentName}: {questionText(execution)}
      </p>
      {execution.options.length > 0 && (
        <div className={exec.options}>
          {execution.options.map((option) => (
            <button
              key={option}
              type="button"
              className={exec.optionButton}
              onClick={() => send(option)}
              title={`"${option}" 로 답하기`}
            >
              {option}
            </button>
          ))}
        </div>
      )}
      <form className={exec.answer} onSubmit={onSubmit}>
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="답을 입력하고 Enter"
          aria-label="답변 입력"
        />
        <button type="submit" disabled={text.trim() === ''}>
          답 보내기
        </button>
      </form>
    </div>
  );
}

/**
 * 구성도 노드나 왼쪽 에이전트 카드에 **말풍선으로 붙는** 입력 대기 팝업.
 * 패널의 블러·스크롤에 잘리지 않도록 body 에 포털로 그린다. 바깥을 누르거나 Esc 로 닫는다.
 */
export default function WaitingInputPopup({
  execution,
  anchor,
  onClose,
}: {
  execution: Execution;
  /** 말풍선(💬) 버튼의 화면 좌표. 팝업을 그 아래에 붙인다. */
  anchor: DOMRect;
  onClose: () => void;
}) {
  const { agents } = useAgentDockStore();

  useEffect(() => {
    const onPointerDown = (e: PointerEvent) => {
      if (!(e.target as HTMLElement).closest('[data-waiting-popup]')) onClose();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKey);
    // 캔버스를 끌거나 굴리면 말풍선이 엉뚱한 곳에 남지 않게 닫는다.
    window.addEventListener('wheel', onClose, true);
    window.addEventListener('scroll', onClose, true);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKey);
      window.removeEventListener('wheel', onClose, true);
      window.removeEventListener('scroll', onClose, true);
    };
  }, [onClose]);

  const left = clamp(anchor.left, 8, window.innerWidth - POPUP_WIDTH - 8);
  const top = clamp(anchor.bottom + 6, 8, window.innerHeight - 140);
  const agentName = agents.find((agent) => agent.id === execution.agentId)?.name ?? '에이전트';

  return createPortal(
    <div
      className={styles.popup}
      data-waiting-popup
      role="dialog"
      aria-label="입력 대기 질문"
      style={{ '--top': `${top}px`, '--left': `${left}px`, '--popup-width': `${POPUP_WIDTH}px` } as CSSProperties}
    >
      <WaitingInputItem execution={execution} agentName={agentName} />
    </div>,
    document.body,
  );
}
