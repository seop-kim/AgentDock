import { useEffect, useRef, useState } from 'react';
import { api } from '../lib/api';
import { logLineKind, type TerminalLine } from '../lib/terminal';
import term from '../styles/terminal.module.css';

/**
 * 터미널 몸통(실행 출력). 그 실행의 **로그 스트림(SSE)** 을 직접 구독한다.
 * 서버는 구독할 때 지금까지의 로그를 먼저 재생하고(메모리 버퍼) 이어서 라이브로 보내며,
 * 실행이 끝나면 `exit` 이름의 이벤트를 보내고 닫는다. 그때까지 줄 끝에 커서가 깜빡인다.
 *
 * <p>`trackTail` 이면 새 줄이 와도 **사용자가 위로 스크롤해 둔 동안에는 따라 내려가지 않는다**(맨 아래일 때만 따라간다).
 * 노드 위 hover 미리보기처럼 안에서 스크롤해 읽는 곳에서 쓴다.
 */
export default function TerminalView({
  executionId,
  live: storeLive,
  className,
  trackTail = false,
}: {
  /** 보여 줄 실행. null 이면 아직 실행이 없다(안내 문구만). */
  executionId: number | null;
  /** 스토어가 본 "돌고 있는가". `exit` 이벤트가 오면 그 뒤로는 멈춘다. */
  live: boolean;
  /** 높이 같은 바깥 모양만 바꾼다 */
  className?: string;
  /** true 면 맨 아래에 있을 때만 새 줄을 따라 내려간다(스크롤해 읽는 미리보기용). */
  trackTail?: boolean;
}) {
  const [lines, setLines] = useState<TerminalLine[]>([]);
  const [finished, setFinished] = useState(false);
  const logRef = useRef<HTMLOListElement>(null);
  // 사용자가 맨 아래(여유 24px 이내)에 있는지. trackTail 일 때만 본다.
  const atBottomRef = useRef(true);

  // 실행이 바뀌면 로그를 처음부터 다시 받는다.
  useEffect(() => {
    setLines([]);
    setFinished(false);
    atBottomRef.current = true;
  }, [executionId]);

  useEffect(() => {
    if (executionId === null) return;
    const source = new EventSource(`${api.base}/executions/${executionId}/stream`);

    // 재생 + 라이브 로그(이름 없는 메시지: `{stream, content}`)
    source.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data as string) as { stream?: string; content?: string };
        setLines((prev) => [...prev, { text: payload.content ?? '', kind: logLineKind(payload.stream ?? 'stdout') }]);
      } catch {
        // JSON 이 아니면 무시한다.
      }
    };

    // 실행 종료(`exit` 이름의 이벤트: `{status, exitCode}`) — 서버가 닫는다.
    source.addEventListener('exit', () => {
      setFinished(true);
      source.close();
    });

    // exit 뒤 서버가 닫으면 onerror 가 오므로 조용히 닫는다(자동 재연결 방지).
    source.onerror = () => source.close();

    return () => source.close();
  }, [executionId]);

  useEffect(() => {
    const el = logRef.current;
    if (!el) return;
    // trackTail 이면 사용자가 위로 스크롤해 둔 동안에는 따라 내려가지 않는다.
    if (!trackTail || atBottomRef.current) el.scrollTop = el.scrollHeight;
  }, [lines.length, trackTail]);

  const live = storeLive && !finished;
  const rows: TerminalLine[] =
    lines.length > 0
      ? lines
      : [
          {
            text: executionId === null ? '지금 실행 중인 작업이 없습니다.' : '출력을 기다리는 중…',
            kind: 'muted',
          },
        ];

  return (
    <ol
      ref={logRef}
      className={`${term.terminal} ${className ?? ''}`}
      // trackTail 일 때만 스크롤 위치를 본다(미리보기에서 위로 올려 읽는 중인지).
      onScroll={
        trackTail
          ? () => {
              const el = logRef.current;
              if (el) atBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 24;
            }
          : undefined
      }
    >
      {rows.map((line, index) => (
        <li key={`${index}-${line.text}`} className={`${term.line} ${term[line.kind]}`}>
          {line.text}
          {live && index === rows.length - 1 && <span className={term.cursor} />}
        </li>
      ))}
    </ol>
  );
}
