import { FormEvent, useState } from 'react';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import styles from './Projects.module.css';

export default function Projects() {
  const { projects, workspaces, createProject, assignWorkspace, removeWorkspace } = useMockStore();
  const [name, setName] = useState('');
  const [assignTarget, setAssignTarget] = useState<number | null>(null);
  const [assignWorkspaceId, setAssignWorkspaceId] = useState('');
  const [assignAsDefault, setAssignAsDefault] = useState(false);

  const onCreate = (e: FormEvent) => {
    e.preventDefault();
    createProject(name.trim());
    setName('');
  };

  const closeAssign = () => {
    setAssignTarget(null);
    setAssignWorkspaceId('');
    setAssignAsDefault(false);
  };

  const onAssign = (projectId: number) => {
    if (!assignWorkspaceId) return;
    assignWorkspace(projectId, Number(assignWorkspaceId), assignAsDefault);
    closeAssign();
  };

  const workspaceById = (id: number) => workspaces.find((w) => w.id === id);

  return (
    <div>
      <h1>Projects</h1>
      <p>
        프로젝트에는 워크스페이스(폴더)를 여러 개 할당할 수 있고, 그중 하나가 기본 작업 디렉터리가 됩니다. 에이전트도
        프로젝트 안에서 만듭니다.
      </p>

      <form onSubmit={onCreate} className={styles.formRow}>
        <input placeholder="프로젝트 이름" value={name} onChange={(e) => setName(e.target.value)} required />
        <button type="submit">등록</button>
      </form>

      <table className={styles.table}>
        <thead>
          <tr>
            <th>Name</th>
            <th>Workspaces</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {projects.map((project) => {
            const assignable = workspaces.filter(
              (w) => !project.workspaces.some((pw) => pw.workspaceId === w.id),
            );
            return (
              <tr key={project.id}>
                <td>{project.name}</td>
                <td>
                  {project.workspaces.length === 0 && <span className={shared.muted}>할당된 워크스페이스 없음</span>}
                  {project.workspaces.map((pw) => {
                    const workspace = workspaceById(pw.workspaceId);
                    return (
                      <div key={pw.workspaceId} className={styles.workspaceRow}>
                        <span>
                          {pw.isDefault ? '★ ' : ''}
                          {workspace?.name} — {workspace?.path}
                        </span>
                        <button onClick={() => removeWorkspace(project.id, pw.workspaceId)}>해제</button>
                      </div>
                    );
                  })}
                </td>
                <td>
                  {assignTarget === project.id ? (
                    <div className={styles.formRow}>
                      <select value={assignWorkspaceId} onChange={(e) => setAssignWorkspaceId(e.target.value)}>
                        <option value="">워크스페이스 선택</option>
                        {assignable.map((w) => (
                          <option key={w.id} value={w.id}>
                            {w.name} — {w.path}
                          </option>
                        ))}
                      </select>
                      <label className={styles.checkLabel}>
                        <input
                          type="checkbox"
                          checked={assignAsDefault}
                          onChange={(e) => setAssignAsDefault(e.target.checked)}
                        />
                        기본으로
                      </label>
                      <button onClick={() => onAssign(project.id)} disabled={!assignWorkspaceId}>
                        할당
                      </button>
                      <button type="button" onClick={closeAssign}>
                        취소
                      </button>
                    </div>
                  ) : (
                    <button onClick={() => setAssignTarget(project.id)} disabled={assignable.length === 0}>
                      워크스페이스 할당
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
