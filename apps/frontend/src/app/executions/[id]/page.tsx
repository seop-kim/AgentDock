'use client';

import { useEffect, useRef, useState } from 'react';
import { api } from '@/lib/api';
import styles from './page.module.css';

interface LogLine {
  stream: 'stdout' | 'stderr';
  content: string;
}

export default function ExecutionPage({ params }: { params: { id: string } }) {
  const [status, setStatus] = useState<string>('PENDING');
  const [lines, setLines] = useState<LogLine[]>([]);
  const logRef = useRef<HTMLPreElement>(null);

  useEffect(() => {
    const source = new EventSource(`${api.base}/executions/${params.id}/stream`);
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
        .getExecution(params.id)
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
  }, [params.id]);

  useEffect(() => {
    logRef.current?.scrollTo(0, logRef.current.scrollHeight);
  }, [lines]);

  return (
    <div>
      <h1>Execution {params.id}</h1>
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
