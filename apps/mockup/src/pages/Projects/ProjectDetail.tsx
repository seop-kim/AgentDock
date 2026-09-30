import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import AgentList from './AgentList';
import GroupBoard from './GroupBoard';
import styles from './ProjectDetail.module.css';
import ProjectWorkspaces from './ProjectWorkspaces';

const NOTICE_MS = 2500;

export default function ProjectDetail() {
  const { id } = useParams();
  const { projects, agents, tasks } = useMockStore();
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (notice === null) return;
    const timer = window.setTimeout(() => setNotice(null), NOTICE_MS);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const project = projects.find((p) => String(p.id) === id);

  if (!project) {
    return (
      <div>
        <Link to="/projects" className={styles.back}>
          ← 프로젝트 목록
        </Link>
        <h1>프로젝트를 찾을 수 없습니다</h1>
      </div>
    );
  }

  const agentCount = agents.filter((a) => a.projectId === project.id).length;
  const taskCount = tasks.filter((t) => t.projectId === project.id).length;

  return (
    <div>
      <Link to="/projects" className={styles.back}>
        ← 프로젝트 목록
      </Link>
      <div className={styles.titleRow}>
        <h1 className={styles.title}>{project.name}</h1>
        <span className={shared.muted}>
          에이전트 {agentCount} · 총 Task {taskCount}
        </span>
      </div>

      {notice && <p className="errorText">{notice}</p>}

      <ProjectWorkspaces project={project} />

      <div className={styles.body}>
        <AgentList project={project} />
        <GroupBoard project={project} onNotice={setNotice} />
      </div>
    </div>
  );
}
