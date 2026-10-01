import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import styles from './ExecutionDetail.module.css';

interface LogLine {
  stream: 'stdout' | 'stderr';
  content: string;
}

export default function ExecutionDetail() {
  const { id } = useParams<{ id: string }>();
  const [status, setStatus] = useState<string>('PENDING');
  const [lines, setLines] = useState<LogLine[]>([]);
  const logRef = useRef<HTMLPreElement>(null);

  useEffect(() => {
    if (!id) return;
    const source = new EventSource(`${api.base}/executions/${id}/stream`);
    source.onmessage = (event) => {
      try {
        const log: LogLine = JSON.parse(event.data);
        setLines((prev) => [...prev, log]);
      } catch {
        // ignore malformed event
      }
    };
    source.onerror = () => {
      source.close();
    };

    const poll = setInterval(() => {
      api
        .getExecution(id)
        .then((exec) => {
          setStatus(exec.status);
          if (exec.status === 'SUCCEEDED' || exec.status === 'FAILED' || exec.status === 'CANCELLED') {
            clearInterval(poll);
          }
        })
        .catch(() => {});
    }, 1500);

    return () => {
      source.close();
      clearInterval(poll);
    };
  }, [id]);

  useEffect(() => {
    logRef.current?.scrollTo(0, logRef.current.scrollHeight);
  }, [lines]);

  return (
    <div>
      <h1>Execution {id}</h1>
      <p>
        Status: <strong>{status}</strong>
      </p>
      <pre ref={logRef} className={styles.log}>
        {lines.map((l, i) => (
          <div key={i} className={l.stream === 'stderr' ? styles.stderr : styles.stdout}>
            {l.content}
          </div>
        ))}
      </pre>
    </div>
  );
}
