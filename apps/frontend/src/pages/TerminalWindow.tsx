import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Agent, Execution, api } from '../lib/api';
import { formatCost, formatDuration, statusClass, statusLabel } from '../lib/executions';
import terminalStyles from '../styles/terminal.module.css';
import styles from './TerminalWindow.module.css';

interface Line {
  stream: string;
  content: string;
}

interface ExitEvent {
  status: string;
  exitCode: number;
}

const BADGE_CLASS: Record<string, string> = {
  active: styles.active,
  wait: styles.wait,
  done: styles.done,
  failed: styles.failed,
};

function lineClass(stream: string): string {
  if (stream === 'system') return terminalStyles.system;
  if (stream === 'stderr') return terminalStyles.error;
  return '';
}

/**
 * 실행 하나의 터미널(새 창). 서버가 저장된 로그를 재생한 뒤 라이브로 이어 보내고,
 * 끝나면 exit 이벤트를 준다. 그래서 창을 나중에 열어도 그 실행 전체가 보인다.
 */
export default function TerminalWindow() {
  const { executionId } = useParams();
  const id = Number(executionId);
  const [execution, setExecution] = useState<Execution | null>(null);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [lines, setLines] = useState<Line[]>([]);
  const [exit, setExit] = useState<ExitEvent | null>(null);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .getExecution(id)
      .then((value) => {
        if (!cancelled) setExecution(value);
      })
      .catch((ex) => {
        if (!cancelled) setError(String(ex));
      });
    api
      .listAgents()
      .then((list) => {
        if (!cancelled) setAgents(list);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [id]);

  // 로그는 SSE 로만 받는다(서버가 저장된 로그를 먼저 재생하므로 중복이 생기지 않는다).
  useEffect(() => {
    const source = new EventSource(`${api.base}/executions/${id}/stream`);
    source.onmessage = (event) => {
      try {
        setLines((prev) => [...prev, JSON.parse(event.data) as Line]);
      } catch {
        // 형식이 어긋난 이벤트는 무시한다.
      }
    };
    source.addEventListener('exit', (event) => {
      try {
        setExit(JSON.parse((event as MessageEvent).data) as ExitEvent);
      } catch {
        // 상태를 못 읽어도 창은 닫지 않는다.
      }
      source.close();
    });
    source.onerror = () => source.close();
    return () => source.close();
  }, [id]);

  // 상태 배지용: exit 이벤트가 오기 전까지 주기적으로 확인한다.
  useEffect(() => {
    if (exit !== null) return;
    const timer = window.setInterval(() => {
      api
        .getExecution(id)
        .then(setExecution)
        .catch(() => {});
    }, 1500);
    return () => window.clearInterval(timer);
  }, [id, exit]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' });
  }, [lines.length]);

  const agentName = useMemo(() => {
    const agentId = execution?.agentId;
    if (agentId === undefined || agentId === null) return '에이전트';
    return agents.find((agent) => agent.id === agentId)?.name ?? `에이전트 ${agentId}`;
  }, [agents, execution]);

  const status = exit?.status ?? execution?.status ?? 'PENDING';
  const running = exit === null && (status === 'PENDING' || status === 'RUNNING' || status === 'WAITING_CHILD');

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <span className={styles.title}>{agentName}</span>
        <span className={`${styles.badge} ${BADGE_CLASS[statusClass(status)]}`}>{statusLabel(status)}</span>
        <span className={styles.meta}>실행 #{id}</span>
        {execution?.costUsd !== null && execution?.costUsd !== undefined && (
          <span className={styles.meta}>
            {formatCost(execution.costUsd)} · {formatDuration(execution.durationMs)}
          </span>
        )}
        {execution?.sessionId !== null && execution?.sessionId !== undefined && (
          <span className={styles.meta}>세션 {execution.sessionId.slice(0, 8)}</span>
        )}
      </header>

      {error !== null && <div className="errorText">{error}</div>}
      {execution !== null && <p className={styles.prompt}>{execution.prompt}</p>}

      <div className={styles.terminalBox}>
        <ul className={`${terminalStyles.terminal} ${styles.terminalBody}`}>
          {lines.map((line, index) => (
            <li key={index} className={`${terminalStyles.line} ${lineClass(line.stream)}`}>
              {line.content}
            </li>
          ))}
          {running && <li className={`${terminalStyles.line} ${terminalStyles.dim}`}>▌</li>}
          <div ref={bottomRef} />
        </ul>
      </div>
    </div>
  );
}
