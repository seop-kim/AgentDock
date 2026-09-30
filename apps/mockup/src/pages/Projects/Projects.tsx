import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import styles from './Projects.module.css';

export default function Projects() {
  const navigate = useNavigate();
  const { projects, workspaces, agents, tasks, createProject } = useMockStore();
  const [name, setName] = useState('');

  const onCreate = (e: FormEvent) => {
    e.preventDefault();
    createProject(name.trim());
    setName('');
  };

  const workspaceName = (id: number) => workspaces.find((w) => w.id === id)?.name ?? '?';

  return (
    <div>
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>프로젝트</h1>
          <p className={shared.hint}>프로젝트를 열면 워크스페이스, 에이전트, 그룹을 관리할 수 있습니다.</p>
        </div>
        <form onSubmit={onCreate} className={styles.createForm}>
          <input placeholder="새 프로젝트 이름" value={name} onChange={(e) => setName(e.target.value)} required />
          <button type="submit">+ 프로젝트 등록</button>
        </form>
      </div>

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>이름</th>
              <th>워크스페이스</th>
              <th className={styles.num}>에이전트</th>
              <th className={styles.num}>총 Task</th>
            </tr>
          </thead>
          <tbody>
            {projects.map((project) => {
              const defaultWorkspace = project.workspaces.find((w) => w.isDefault);
              const extra = project.workspaces.length - 1;
              return (
                <tr key={project.id} className={styles.row} onClick={() => navigate(`/projects/${project.id}`)}>
                  <td>
                    <Link to={`/projects/${project.id}`} className={styles.name} onClick={(e) => e.stopPropagation()}>
                      {project.name}
                    </Link>
                  </td>
                  <td>
                    {defaultWorkspace ? (
                      <span>
                        ★ {workspaceName(defaultWorkspace.workspaceId)}
                        {extra > 0 && <span className={shared.muted}> 외 {extra}개</span>}
                      </span>
                    ) : (
                      <span className={shared.muted}>할당 없음</span>
                    )}
                  </td>
                  <td className={styles.num}>{agents.filter((a) => a.projectId === project.id).length}</td>
                  <td className={styles.num}>{tasks.filter((t) => t.projectId === project.id).length}</td>
                </tr>
              );
            })}
            {projects.length === 0 && (
              <tr>
                <td colSpan={4} className={styles.empty}>
                  등록된 프로젝트가 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
