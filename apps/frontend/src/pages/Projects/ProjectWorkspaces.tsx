import { useEffect, useState } from 'react';
import { api, type WorkspaceRuntime } from '../../lib/api';
import { useAgentDockStore } from '../../store/AgentDockStore';
import shared from '../../styles/shared.module.css';
import type { Project } from '../../types';
import styles from './ProjectWorkspaces.module.css';

/** (폴더, 런타임) 상태 → 배지 문구/색. 백엔드 값은 `CONNECTED`/`DISCONNECTED`/`ERROR`. */
function runtimeBadge(status: string): { label: string; tone: string } {
  if (status === 'CONNECTED') return { label: '연결됨', tone: shared.badgeOk };
  if (status === 'ERROR') return { label: '오류', tone: shared.badgeError };
  return { label: '확인 필요', tone: '' };
}

/** 경로의 마지막 조각을 워크스페이스 이름으로 쓴다(윈도우/유닉스 구분자를 모두 받는다). */
function folderName(path: string): string {
  const trimmed = path.replace(/[\\/]+$/, '');
  const parts = trimmed.split(/[\\/]/);
  return parts[parts.length - 1] || trimmed;
}

/**
 * 프로젝트 워크스페이스 할당/해제. 프로젝트 설정 창 안에서만 쓴다.
 * 목업에 없던 두 가지를 더한다(계획 3장): 폴더 등록(`registerWorkspace`)과
 * 폴더별 실행 가능 확인(`checkWorkspaceRuntime`) — 실행 가드가 (폴더, 런타임) 상태를 요구하기 때문이다.
 */
export default function ProjectWorkspaces({ project }: { project: Project }) {
  const { workspaces, providers, assignWorkspace, removeWorkspace, registerWorkspace } = useAgentDockStore();
  const [assigning, setAssigning] = useState(false);
  const [workspaceId, setWorkspaceId] = useState('');
  const [asDefault, setAsDefault] = useState(false);
  const [path, setPath] = useState('');
  const [registering, setRegistering] = useState(false);
  /** 워크스페이스 id → 그 폴더의 런타임 상태 목록. */
  const [runtimes, setRuntimes] = useState<Record<number, WorkspaceRuntime[]>>({});
  /** 지금 확인 중인 워크스페이스 id(확인은 최대 30초 걸린다). */
  const [checking, setChecking] = useState<number | null>(null);

  const assignable = workspaces.filter((w) => !project.workspaces.some((pw) => pw.workspaceId === w.id));
  const enabledProviders = providers.filter((p) => p.enabled);
  const workspaceKey = project.workspaces.map((pw) => pw.workspaceId).join(',');

  // 할당된 각 폴더의 (폴더, 런타임) 상태를 읽어 둔다.
  useEffect(() => {
    let alive = true;
    project.workspaces.forEach((pw) => {
      api
        .listWorkspaceRuntimes(pw.workspaceId)
        .then((list) => {
          if (alive) setRuntimes((prev) => ({ ...prev, [pw.workspaceId]: list }));
        })
        .catch(() => {});
    });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceKey]);

  const close = () => {
    setAssigning(false);
    setWorkspaceId('');
    setAsDefault(false);
  };

  const onAssign = () => {
    if (!workspaceId) return;
    assignWorkspace(project.id, Number(workspaceId), asDefault);
    close();
  };

  /** 폴더를 등록한다(이름은 마지막 경로 조각). 등록하면 다른 워크스페이스처럼 할당할 수 있다. */
  const onRegister = () => {
    const trimmed = path.trim();
    if (trimmed === '') return;
    setRegistering(true);
    void registerWorkspace(folderName(trimmed), trimmed)
      .then(() => setPath(''))
      .finally(() => setRegistering(false));
  };

  /** 이 폴더를 작업 디렉터리로 켜 둔 런타임을 실제로 한 번 실행해 확인한다(최대 30초). */
  const onCheck = (targetId: number) => {
    if (enabledProviders.length === 0) return;
    setChecking(targetId);
    Promise.all(
      enabledProviders.map((provider) => api.checkWorkspaceRuntime(targetId, provider.id).catch(() => null)),
    )
      .then((results) => {
        setRuntimes((prev) => {
          const next = [...(prev[targetId] ?? [])];
          results.forEach((result) => {
            if (result === null) return;
            const index = next.findIndex((item) => item.providerId === result.providerId);
            if (index >= 0) next[index] = result;
            else next.push(result);
          });
          return { ...prev, [targetId]: next };
        });
      })
      .finally(() => setChecking(null));
  };

  return (
    <div>
      {project.workspaces.length === 0 && (
        <p className={shared.muted}>할당된 워크스페이스가 없습니다. 기본 워크스페이스가 있어야 에이전트를 실행할 수 있습니다.</p>
      )}
      <ul className={styles.list}>
        {project.workspaces.map((pw) => {
          const workspace = workspaces.find((w) => w.id === pw.workspaceId);
          return (
            <li key={pw.workspaceId} className={styles.item}>
              <span className={pw.isDefault ? styles.defaultMark : styles.defaultMarkOff}>★</span>
              <div className={styles.text}>
                <span className={styles.name}>{workspace?.name}</span>
                <span className={styles.path}>{workspace?.path}</span>
              </div>
              {pw.isDefault && <span className={`${shared.badge} ${shared.badgeOk}`}>기본</span>}
              <span className={styles.runtimeStatus}>
                {enabledProviders.length === 0 ? (
                  <span className={shared.muted}>켜 둔 런타임 없음</span>
                ) : (
                  enabledProviders.map((provider) => {
                    const runtime = (runtimes[pw.workspaceId] ?? []).find((item) => item.providerId === provider.id);
                    const badge = runtimeBadge(runtime?.status ?? 'DISCONNECTED');
                    return (
                      <span
                        key={provider.id}
                        className={`${shared.badge} ${badge.tone}`}
                        title={runtime?.lastError ?? `${provider.name} · 이 폴더에서의 마지막 확인 결과`}
                      >
                        {provider.name} {badge.label}
                      </span>
                    );
                  })
                )}
              </span>
              <button
                onClick={() => onCheck(pw.workspaceId)}
                disabled={enabledProviders.length === 0 || checking !== null}
                title="이 폴더를 작업 디렉터리로 런타임을 한 번 실행해 확인합니다(최대 30초)"
              >
                {checking === pw.workspaceId ? '확인 중…' : '이 폴더에서 확인'}
              </button>
              <button className={shared.dangerButton} onClick={() => removeWorkspace(project.id, pw.workspaceId)}>
                해제
              </button>
            </li>
          );
        })}
      </ul>

      <div className={styles.registerRow}>
        <input
          value={path}
          onChange={(e) => setPath(e.target.value)}
          placeholder="폴더 경로 (예: C:\work\agentdock)"
          aria-label="등록할 폴더 경로"
        />
        <button onClick={onRegister} disabled={path.trim() === '' || registering}>
          {registering ? '등록 중…' : '+ 폴더 등록'}
        </button>
      </div>

      {assigning ? (
        <div className={styles.assignRow}>
          <select value={workspaceId} onChange={(e) => setWorkspaceId(e.target.value)}>
            <option value="">워크스페이스 선택</option>
            {assignable.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name} — {w.path}
              </option>
            ))}
          </select>
          <label className={styles.checkLabel}>
            <input type="checkbox" checked={asDefault} onChange={(e) => setAsDefault(e.target.checked)} />
            기본으로
          </label>
          <button onClick={onAssign} disabled={!workspaceId}>
            할당
          </button>
          <button type="button" onClick={close}>
            취소
          </button>
        </div>
      ) : (
        <button className={styles.addButton} onClick={() => setAssigning(true)} disabled={assignable.length === 0}>
          + 워크스페이스 할당
        </button>
      )}
    </div>
  );
}
