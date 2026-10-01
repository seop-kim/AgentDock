import { useEffect, useRef, useState } from 'react';
import { api } from '../../lib/api';
import shared from '../../styles/shared.module.css';
import styles from './CommandPanel.module.css';

export type CommandKind = 'login' | 'install';

/**
 * 로그인/설치 세션의 출력을 백엔드 SSE(`/ai-providers/command-sessions/{id}/stream`)로 받아 보여 준다.
 * 서버는 `{type:'output', content}` 을 흘려 보내고 끝나면 `{type:'exit', exitCode}` 를 보낸 뒤 닫는다.
 * 입력이 필요한 단계(로그인 코드 등)는 그대로 stdin(`.../input`)으로 보낸다.
 */
export default function CommandPanel({
  kind,
  sessionId,
  onClose,
}: {
  kind: CommandKind;
  sessionId: string;
  onClose: () => void;
}) {
  const [lines, setLines] = useState<string[]>([]);
  const [exitCode, setExitCode] = useState<number | null>(null);
  const [input, setInput] = useState('');
  const outputRef = useRef<HTMLPreElement>(null);

  useEffect(() => {
    setLines([]);
    setExitCode(null);
    const source = new EventSource(`${api.base}/ai-providers/command-sessions/${sessionId}/stream`);
    source.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data as string) as {
          type?: string;
          content?: string | null;
          exitCode?: number | null;
        };
        if (payload.type === 'exit') {
          setExitCode(payload.exitCode ?? 0);
          source.close();
          return;
        }
        const content = payload.content;
        if (payload.type === 'output' && typeof content === 'string') {
          setLines((prev) => [...prev, content]);
        }
      } catch {
        // JSON 이 아니면 무시한다.
      }
    };
    // exit 뒤 서버가 닫으면 onerror 가 오므로 조용히 닫는다(자동 재연결 방지).
    source.onerror = () => source.close();
    return () => source.close();
  }, [sessionId]);

  useEffect(() => {
    const el = outputRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [lines]);

  const onSend = () => {
    if (input.trim() === '' || exitCode !== null) return;
    const text = input;
    setLines((prev) => [...prev, `< ${text}`]);
    setInput('');
    api.sendSessionInput(sessionId, text).catch(() => {});
  };

  return (
    <div className={shared.panel}>
      <p className={shared.muted}>{kind === 'login' ? '로그인' : 'CLI 설치'} 출력</p>
      <pre ref={outputRef} className={styles.output}>
        {lines.length === 0 ? '시작하는 중...' : lines.join('\n')}
        {exitCode !== null && `\n[종료 코드 ${exitCode}]`}
      </pre>
      <div className={styles.inputRow}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && onSend()}
          placeholder="입력이 필요하면 여기에 (로그인 코드 등)"
          disabled={exitCode !== null}
        />
        <button onClick={onSend} disabled={exitCode !== null}>
          보내기
        </button>
        <button onClick={onClose}>닫기</button>
      </div>
    </div>
  );
}
