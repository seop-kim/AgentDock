import { FormEvent, ReactNode, useEffect, useRef, useState } from 'react';
import { api } from '../lib/api';
import styles from './Providers.module.css';

export type CommandKind = 'login' | 'install';

interface CommandPanelProps {
  providerId: number;
  kind: CommandKind;
  /** 설치처럼 순서대로 실행될 단계들. 패널에 그대로 보여 준다. */
  commands?: string[] | null;
  onClose: () => void;
  onExit: (exitCode: number) => void;
}

interface SessionEvent {
  type: 'output' | 'exit';
  content: string | null;
  exitCode: number | null;
}

// eslint 없이도 읽기 쉽게: ANSI 이스케이프 제거 후 http(s) URL 만 링크로 만든다
const ANSI_PATTERN = /\u001b\[[0-9;?]*[ -/]*[@-~]/g;
const URL_PATTERN = /(https?:\/\/[^\s]+)/g;

function renderOutput(text: string): ReactNode[] {
  return text
    .replace(ANSI_PATTERN, '')
    .split(URL_PATTERN)
    .map((part, index) =>
      index % 2 === 1 ? (
        <a key={index} href={part} target="_blank" rel="noreferrer">
          {part}
        </a>
      ) : (
        part
      ),
    );
}

/** 런타임 CLI 명령(로그인/설치)을 실행하고 출력을 SSE 로 보여 주는 패널. */
export default function CommandPanel({ providerId, kind, commands, onClose, onExit }: CommandPanelProps) {
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [output, setOutput] = useState('');
  const [exitCode, setExitCode] = useState<number | null>(null);
  const [input, setInput] = useState('');
  const [error, setError] = useState<string | null>(null);
  const onExitRef = useRef(onExit);
  onExitRef.current = onExit;

  useEffect(() => {
    let source: EventSource | null = null;
    let startedId: string | null = null;
    let cancelled = false;

    const start = kind === 'install' ? api.startInstall(providerId) : api.startLogin(providerId);
    start
      .then(({ sessionId: id }) => {
        // 취소된 실행(개발 모드 StrictMode 재실행 등)은 같은 세션을 공유하므로 여기서 세션을 끊지 않는다
        if (cancelled) return;
        startedId = id;
        setSessionId(id);
        source = new EventSource(`${api.base}/ai-providers/command-sessions/${id}/stream`);
        source.onmessage = (event) => {
          try {
            const data: SessionEvent = JSON.parse(event.data);
            if (data.type === 'output') {
              setOutput((prev) => prev + (data.content ?? ''));
            } else if (data.type === 'exit') {
              setExitCode(data.exitCode);
              source?.close();
              onExitRef.current(data.exitCode ?? -1);
            }
          } catch {
            // ignore malformed event
          }
        };
        source.onerror = () => source?.close();
      })
      .catch((e) => setError(String(e)));

    return () => {
      cancelled = true;
      source?.close();
      if (startedId) api.stopSession(startedId).catch(() => {});
    };
  }, [providerId, kind]);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!sessionId) return;
    setError(null);
    try {
      await api.sendSessionInput(sessionId, input);
      setInput('');
    } catch (err) {
      setError(String(err));
    }
  };

  const title = kind === 'install' ? 'CLI 설치' : '로그인';

  return (
    <div className={styles.loginPanel}>
      <p className={styles.checkedAt}>
        {title}
        {kind === 'install' && commands && commands.length > 0 ? ` — 실행할 명령: ${commands.join(' → ')}` : ''}
      </p>
      <pre className={styles.output}>{output ? renderOutput(output) : `${title} 명령을 시작하는 중...`}</pre>
      {exitCode !== null && (
        <p className={styles.checkedAt}>
          {exitCode === 0
            ? kind === 'install'
              ? '설치가 끝났습니다. 이어서 이 폴더에서 다시 확인합니다.'
              : '로그인 명령이 끝났습니다. 워크스페이스에서 "이 폴더에서 확인"을 눌러 상태를 갱신하세요.'
            : `${title} 명령이 종료되었습니다 (exit ${exitCode}).`}
        </p>
      )}
      {error && <p className="errorText">{error}</p>}
      <form className={styles.inputRow} onSubmit={onSubmit}>
        <input
          placeholder="입력이 필요하면 여기에 붙여 넣고 전송"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          disabled={!sessionId || exitCode !== null}
        />
        <button type="submit" disabled={!sessionId || exitCode !== null || input === ''}>
          전송
        </button>
        <button type="button" onClick={onClose}>
          닫기
        </button>
      </form>
    </div>
  );
}
