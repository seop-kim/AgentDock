import { useEffect, useRef, useState } from 'react';
import shared from '../../styles/shared.module.css';
import styles from './CommandPanel.module.css';

export type CommandKind = 'login' | 'install';

const LINE_DELAY_MS = 500;

const LOGIN_LINES = [
  '> claude auth login',
  '브라우저에서 아래 주소를 열어 로그인하세요.',
  'https://claude.ai/oauth/authorize?mock=1',
  '로그인 코드를 기다리는 중...',
  '로그인되었습니다.',
];

const buildInstallLines = (commands: string[]) =>
  commands.flatMap((command) => [`> ${command}`, 'added 1 package in 3s']);

/** 로그인/설치 출력을 타이머로 한 줄씩 흘려 보내 SSE 스트리밍처럼 보이게 한다(가짜). */
export default function CommandPanel({
  kind,
  commands,
  onClose,
}: {
  kind: CommandKind;
  commands: string[];
  onClose: () => void;
}) {
  const [lines, setLines] = useState<string[]>([]);
  const [exitCode, setExitCode] = useState<number | null>(null);
  const [input, setInput] = useState('');
  const outputRef = useRef<HTMLPreElement>(null);

  useEffect(() => {
    const script = kind === 'login' ? LOGIN_LINES : buildInstallLines(commands);
    let index = 0;
    setLines([]);
    setExitCode(null);
    const timer = window.setInterval(() => {
      if (index < script.length) {
        const line = script[index];
        setLines((prev) => [...prev, line]);
        index += 1;
      } else {
        window.clearInterval(timer);
        setExitCode(0);
      }
    }, LINE_DELAY_MS);
    return () => window.clearInterval(timer);
  }, [kind, commands]);

  useEffect(() => {
    outputRef.current?.scrollTo({ top: outputRef.current.scrollHeight });
  }, [lines]);

  const onSend = () => {
    if (input.trim() === '') return;
    setLines((prev) => [...prev, `< ${input}`]);
    setInput('');
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
