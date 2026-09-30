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
/** 에이전트 패널 너비(320) + 좌우 여백(16 + 16). 구성도 맞춤이 이만큼을 비켜 간다. */
const AGENT_PANEL_INSET = 352;
const NO_PANEL_INSET = 24;

/**
 * 프로젝트 상세. 구성도가 화면 전체를 채우고, 헤더/에이전트 패널/설정 버튼/도구 막대가 그 위에 떠 있다.
 */
export default function ProjectDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { projects, workspaces, agents, groups, tasks } = useMockStore();
  const [notice, setNotice] = useState<string | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  // 에이전트 패널은 기본으로 열려 있고, 필요할 때만 접는다.
  const [agentsOpen, setAgentsOpen] = useState(true);

  useEffect(() => {
    if (notice === null) return;
    const timer = window.setTimeout(() => setNotice(null), NOTICE_MS);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const project = projects.find((p) => String(p.id) === id);

  if (!project) {
    return (
      <div className={styles.notFound}>
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
  const agentCount = agents.filter((a) => a.projectId === project.id).length;
  const stats = [
    { label: '에이전트', value: agentCount },
    { label: '그룹', value: groups.filter((g) => g.projectId === project.id).length },
    { label: '총 Task', value: tasks.filter((t) => t.projectId === project.id).length },
  ];

  return (
    <div className={styles.stage}>
      <GroupCanvas
        project={project}
        insetLeft={agentsOpen ? AGENT_PANEL_INSET : NO_PANEL_INSET}
        onNotice={setNotice}
      />

      <div className={styles.leftColumn}>
        <header className={styles.header}>
          <Link to="/projects" className={styles.back}>
            ← 프로젝트 목록
          </Link>
          <div className={styles.titleRow}>
            <h1 className={styles.title}>{project.name}</h1>
            <button
              type="button"
              className={styles.settingsButton}
              onClick={() => setSettingsOpen(true)}
              aria-label="프로젝트 설정"
              title="프로젝트 설정"
            >
              <SettingsIcon />
            </button>
          </div>
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
        </header>

        {agentsOpen ? (
          <AgentList project={project} onCollapse={() => setAgentsOpen(false)} />
        ) : (
          <button type="button" className={styles.openAgents} onClick={() => setAgentsOpen(true)}>
            에이전트 {agentCount} ›
          </button>
        )}
      </div>

      {notice && <p className={`errorText ${styles.notice}`}>{notice}</p>}

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
