import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { SettingsIcon } from '../../components/icons';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import AgentList from './AgentList';
import GroupCanvas from './GroupCanvas';
import styles from './ProjectDetail.module.css';
import ProjectSettingsModal from './ProjectSettingsModal';

const NOTICE_MS = 2500;

export default function ProjectDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { projects, workspaces, agents, groups, tasks } = useMockStore();
  const [notice, setNotice] = useState<string | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);

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

  const defaultWorkspace = project.workspaces.find((w) => w.isDefault);
  const workspaceName = workspaces.find((w) => w.id === defaultWorkspace?.workspaceId)?.name;
  const extra = project.workspaces.length - 1;
  const stats = [
    { label: '에이전트', value: agents.filter((a) => a.projectId === project.id).length },
    { label: '그룹', value: groups.filter((g) => g.projectId === project.id).length },
    { label: '총 Task', value: tasks.filter((t) => t.projectId === project.id).length },
  ];

  return (
    <div>
      <Link to="/projects" className={styles.back}>
        ← 프로젝트 목록
      </Link>

      <header className={styles.header}>
        <div className={styles.headerMain}>
          <h1 className={styles.title}>{project.name}</h1>
          <div className={styles.meta}>
            <span className={styles.workspace}>
              {workspaceName ? (
                <>
                  ★ {workspaceName}
                  {extra > 0 && <span className={shared.muted}> 외 {extra}개</span>}
                </>
              ) : (
                <span className={shared.muted}>워크스페이스 없음</span>
              )}
            </span>
            {stats.map((stat) => (
              <span key={stat.label} className={styles.stat}>
                <strong>{stat.value}</strong> {stat.label}
              </span>
            ))}
          </div>
        </div>
        <button type="button" className={styles.settingsButton} onClick={() => setSettingsOpen(true)}>
          <SettingsIcon />
          설정
        </button>
      </header>

      {notice && <p className="errorText">{notice}</p>}

      <div className={styles.body}>
        <AgentList project={project} />
        <GroupCanvas project={project} onNotice={setNotice} />
      </div>

      {settingsOpen && (
        <ProjectSettingsModal
          project={project}
          onClose={() => setSettingsOpen(false)}
          onDeleted={() => navigate('/projects')}
        />
      )}
    </div>
  );
}
