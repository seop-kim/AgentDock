import { FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAgentDockStore } from '../../store/AgentDockStore';
import shared from '../../styles/shared.module.css';
import styles from './Projects.module.css';

export default function Projects() {
  const { projects, workspaces, agents, tasks, createProject, loading } = useAgentDockStore();
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

      {loading && <p className={shared.muted}>불러오는 중…</p>}
      {!loading && projects.length === 0 && <p className={shared.muted}>등록된 프로젝트가 없습니다.</p>}

      <div className={styles.grid}>
        {projects.map((project) => {
          const defaultWorkspace = project.workspaces.find((w) => w.isDefault);
          const extra = project.workspaces.length - 1;
          const agentCount = agents.filter((a) => a.projectId === project.id).length;
          const taskCount = tasks.filter((t) => t.projectId === project.id).length;
          return (
            <Link key={project.id} to={`/projects/${project.id}`} className={styles.card}>
              <h2 className={styles.cardTitle}>{project.name}</h2>

              <div className={styles.workspace}>
                <span className={styles.label}>워크스페이스</span>
                {defaultWorkspace ? (
                  <span className={styles.workspaceName}>
                    ★ {workspaceName(defaultWorkspace.workspaceId)}
                    {extra > 0 && <span className={shared.muted}> 외 {extra}개</span>}
                  </span>
                ) : (
                  <span className={shared.muted}>할당 없음</span>
                )}
              </div>

              <div className={styles.stats}>
                <div className={styles.stat}>
                  <span className={styles.statValue}>{agentCount}</span>
                  <span className={styles.label}>에이전트</span>
                </div>
                <div className={styles.stat}>
                  <span className={styles.statValue}>{taskCount}</span>
                  <span className={styles.label}>총 Task</span>
                </div>
              </div>
            </Link>
          );
        })}
      </div>
    </div>
  );
}
