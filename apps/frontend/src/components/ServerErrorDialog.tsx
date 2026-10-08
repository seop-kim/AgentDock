import { useEffect, useMemo, useState } from 'react';
import { api, type ServerError } from '../lib/api';
import { useAgentDockStore } from '../store/AgentDockStore';
import { toast } from './Toaster';
import styles from './ServerErrorDialog.module.css';

/** "무시"한 오류 id — 다시 뜨지 않게 브라우저에 남긴다. */
const DISMISSED_KEY = 'agentdock.dismissedServerErrors';

function readDismissed(): number[] {
  try {
    const raw = window.localStorage.getItem(DISMISSED_KEY);
    const parsed = raw === null ? [] : (JSON.parse(raw) as unknown);
    return Array.isArray(parsed) ? parsed.filter((value): value is number => typeof value === 'number') : [];
  } catch {
    return [];
  }
}

/** 마스터에게 보낼 문장 — 재현 경로·원인 정리와 재발 방지까지 요구한다. */
function textFor(item: ServerError): string {
  return [
    '서버 내부 오류를 고쳐 주세요.',
    '',
    `[오류] ${item.time} ${item.method} ${item.path}`,
    `[예외] ${item.exception}: ${item.message}`,
    '[스택]',
    item.stack,
    '',
    '재현 경로와 원인을 정리하고, 재발 방지까지 포함해 수정하세요.',
  ].join('\n');
}

/**
 * 서버 내부 오류(5xx)를 보여 주는 **사용자 확인 창**.
 *
 * <p>원인을 사람이 읽고 **에이전트에게 수정을 맡길지** 고른다 — 위임은 새 API 가 아니라 기존 명령 경로를 그대로
 * 쓰므로(마스터가 트리를 만든다) 워크트리 격리·검증·커밋·병합이 다 적용된다. **자동 전송은 하지 않는다.**
 */
export default function ServerErrorDialog() {
  const { error, projects, sendCommand } = useAgentDockStore();
  const [items, setItems] = useState<ServerError[]>([]);
  const [dismissed, setDismissed] = useState<number[]>(() => readDismissed());
  const [projectId, setProjectId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(true);

  // 20초마다 + 요청이 실패할 때마다(스토어의 error 변화) 최근 오류를 다시 읽는다.
  useEffect(() => {
    let alive = true;
    const load = () => {
      void api
        .listServerErrors()
        .then((rows) => {
          if (alive) setItems(rows);
        })
        .catch(() => {
          // 조회 실패는 조용히 넘긴다(창 자체가 부가 기능이다).
        });
    };
    load();
    const timer = window.setInterval(load, 20000);
    return () => {
      alive = false;
      window.clearInterval(timer);
    };
  }, [error]);

  // 프로젝트가 하나뿐이면 그것을 쓴다(여러 개면 고르게 한다).
  useEffect(() => {
    if (projectId === null && projects.length > 0) setProjectId(projects[0].id);
  }, [projects, projectId]);

  // "나중에"로 닫으면 다음 오류가 들어올 때 다시 뜬다(무시한 것은 영구히 숨긴다).
  useEffect(() => {
    if (items.some((item) => !dismissed.includes(item.id))) setOpen(true);
  }, [items, dismissed]);

  const current = useMemo(
    () => (open ? (items.find((item) => !dismissed.includes(item.id)) ?? null) : null),
    [items, dismissed, open],
  );
  if (current === null || current.exception === '') return null;

  const project = projects.find((candidate) => candidate.id === projectId) ?? null;

  const dismiss = (forever: boolean) => {
    if (forever) {
      const next = [...dismissed, current.id].slice(-100);
      setDismissed(next);
      window.localStorage.setItem(DISMISSED_KEY, JSON.stringify(next));
    }
    setOpen(false);
  };

  const handOver = () => {
    if (project === null || project.masterAgentId === null) {
      toast('이 프로젝트에 마스터 에이전트가 없어 맡길 수 없습니다', 'warn');
      return;
    }
    setBusy(true);
    sendCommand(project.id, { kind: 'agent', id: project.masterAgentId }, textFor(current), []);
    toast(`서버 오류 #${current.id} 수정을 ${project.name} 마스터에게 맡겼습니다`, 'success');
    dismiss(true);
    setBusy(false);
  };

  return (
    <div className={styles.overlay} role="alertdialog" aria-label="서버 내부 오류">
      <div className={styles.card}>
        <div className={styles.head}>
          <span className={styles.title}>서버 내부 오류가 발생했습니다</span>
          <span className={styles.count}>
            {new Date(current.time).toLocaleString('ko-KR')} · #{current.id}
          </span>
        </div>
        <div className={styles.meta}>
          {current.method} {current.path}
        </div>
        <div className={styles.message}>
          {current.exception}: {current.message}
        </div>
        {/* 스택은 읽기만 하면 되므로 접지 않고 스크롤로 둔다(창이 넘치지 않게). */}
        <pre className={styles.stack}>{current.stack}</pre>
        <div className={styles.actions}>
          {projects.length > 1 && (
            <select
              value={projectId ?? ''}
              onChange={(event) => setProjectId(Number(event.target.value))}
              aria-label="맡길 프로젝트"
            >
              {projects.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.name}
                </option>
              ))}
            </select>
          )}
          <button type="button" onClick={() => dismiss(false)}>
            나중에
          </button>
          <button type="button" onClick={() => dismiss(true)}>
            무시
          </button>
          <button type="button" disabled={busy} onClick={handOver} title="마스터가 원인을 정리하고 고칩니다">
            에이전트에게 수정 맡기기
          </button>
        </div>
      </div>
    </div>
  );
}
