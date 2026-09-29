import { FormEvent, ReactNode, useEffect, useRef, useState } from 'react';
import { api } from '../lib/api';
import styles from './Providers.module.css';

interface LoginPanelProps {
  providerId: number;
  onClose: () => void;
  onExit: (exitCode: number) => void;
}

interface LoginEventData {
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

export default function LoginPanel({ providerId, onClose, onExit }: LoginPanelProps) {
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

    api
      .startLogin(providerId)
      .then(({ sessionId: id }) => {
        // 취소된 실행(개발 모드 StrictMode 재실행 등)은 같은 세션을 공유하므로 여기서 세션을 끊지 않는다
        if (cancelled) return;
        startedId = id;
        setSessionId(id);
        source = new EventSource(`${api.base}/ai-providers/login-sessions/${id}/stream`);
        source.onmessage = (event) => {
          try {
            const data: LoginEventData = JSON.parse(event.data);
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
      if (startedId) api.stopLogin(startedId).catch(() => {});
    };
  }, [providerId]);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!sessionId) return;
    setError(null);
    try {
      await api.sendLoginInput(sessionId, input);
      setInput('');
    } catch (err) {
      setError(String(err));
    }
  };

  return (
    <div className={styles.loginPanel}>
      <pre className={styles.output}>{output ? renderOutput(output) : '로그인 명령을 시작하는 중...'}</pre>
      {exitCode !== null && (
        <p className={styles.checkedAt}>
          {exitCode === 0
            ? '로그인 명령이 끝났습니다. 각 워크스페이스에서 "이 폴더에서 확인"을 눌러 상태를 갱신하세요.'
            : `로그인 명령이 종료되었습니다 (exit ${exitCode}).`}
        </p>
      )}
      {error && <p className="errorText">{error}</p>}
      <form className={styles.inputRow} onSubmit={onSubmit}>
        <input
          placeholder="인증 코드 등 입력이 필요하면 여기에 붙여 넣고 전송"
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
