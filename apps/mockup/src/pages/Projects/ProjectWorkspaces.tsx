import { useState } from 'react';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import type { Project } from '../../types';
import styles from './ProjectDetail.module.css';

/** 프로젝트 상세의 워크스페이스 영역. 할당/해제는 여기서만 한다. */
export default function ProjectWorkspaces({ project }: { project: Project }) {
  const { workspaces, assignWorkspace, removeWorkspace } = useMockStore();
  const [assigning, setAssigning] = useState(false);
  const [workspaceId, setWorkspaceId] = useState('');
  const [asDefault, setAsDefault] = useState(false);

  const assignable = workspaces.filter((w) => !project.workspaces.some((pw) => pw.workspaceId === w.id));

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

  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <h2 className={styles.panelTitle}>워크스페이스</h2>
        {!assigning && (
          <button onClick={() => setAssigning(true)} disabled={assignable.length === 0}>
            + 할당
          </button>
        )}
      </div>

      {project.workspaces.length === 0 && (
        <p className={shared.muted}>할당된 워크스페이스가 없습니다. 기본 워크스페이스가 있어야 에이전트를 실행할 수 있습니다.</p>
      )}
      <ul className={styles.workspaceList}>
        {project.workspaces.map((pw) => {
          const workspace = workspaces.find((w) => w.id === pw.workspaceId);
          return (
            <li key={pw.workspaceId} className={styles.workspaceItem}>
              <span className={pw.isDefault ? styles.defaultMark : styles.defaultMarkOff}>★</span>
              <span className={styles.workspaceName}>{workspace?.name}</span>
              <span className={styles.workspacePath}>{workspace?.path}</span>
              {pw.isDefault && <span className={`${shared.badge} ${shared.badgeOk}`}>기본</span>}
              <button className={shared.dangerButton} onClick={() => removeWorkspace(project.id, pw.workspaceId)}>
                해제
              </button>
            </li>
          );
        })}
      </ul>

      {assigning && (
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
      )}
    </section>
  );
}
