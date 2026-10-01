import { useEffect, useRef, useState } from 'react';
import { tailLine, terminalLines, terminalState, type TerminalLine } from '../lib/terminal';
import term from '../styles/terminal.module.css';
import type { Agent, AgentGroup, Execution, Task } from '../types';

/** 스스로 늘어나는 간격과 최대 줄 수(살아 있는 느낌만 준다). */
const TAIL_MS = 900;
const TAIL_MAX = 40;

/**
 * 터미널 몸통(실행 출력). 돌고 있는 실행이면 줄이 계속 늘어나고 끝에 커서가 깜빡인다.
 * 터미널 창(새 창)과 실행 상세가 함께 쓴다.
 */
export default function TerminalView({
  agent,
  execution,
  task,
  groups,
  cwd,
  className,
}: {
  agent: Agent;
  execution: Execution | null;
  task: Task | null;
  groups: AgentGroup[];
  cwd: string | null;
  /** 높이 같은 바깥 모양만 바꾼다 */
  className?: string;
}) {
  const live = terminalState(execution, task) === 'live';
  const head = terminalLines({ agent, execution, task, groups, cwd });

  const [tail, setTail] = useState<TerminalLine[]>([]);
  useEffect(() => {
    if (!live) return;
    const timer = window.setInterval(() => {
      setTail((prev) => (prev.length >= TAIL_MAX ? prev : [...prev, tailLine(prev.length + (execution?.id ?? 0))]));
    }, TAIL_MS);
    return () => window.clearInterval(timer);
  }, [live, execution?.id]);

  const logRef = useRef<HTMLOListElement>(null);
  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight });
  }, [head.length, tail.length]);

  const lines = [...head, ...tail];

  return (
    <ol ref={logRef} className={`${term.terminal} ${className ?? ''}`}>
      {lines.map((line, index) => (
        <li key={`${index}-${line.text}`} className={`${term.line} ${term[line.kind]}`}>
          {line.text}
          {live && index === lines.length - 1 && <span className={term.cursor} />}
        </li>
      ))}
    </ol>
  );
}
