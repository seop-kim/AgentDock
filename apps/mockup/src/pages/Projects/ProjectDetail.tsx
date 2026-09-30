import { CSSProperties, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ChevronDownIcon, ChevronUpIcon, SettingsIcon } from '../../components/icons';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import type { ChatTarget } from '../../types';
import AgentList from './AgentList';
import ChatPanel from './ChatPanel';
import GroupCanvas from './GroupCanvas';
import GroupList from './GroupList';
import styles from './ProjectDetail.module.css';
import ProjectSettingsModal from './ProjectSettingsModal';

const NOTICE_MS = 2500;
/** 에이전트 패널 너비(320) + 좌우 여백(16 + 16). 구성도 맞춤이 이만큼을 비켜 간다. */
const AGENT_PANEL_INSET = 352;
const NO_PANEL_INSET = 24;
/** 채팅 창이 구성도를 가리는 높이: 입력줄만 있을 때 / 기록을 펼쳤을 때 */
const CHAT_COLLAPSED_INSET = 120;
const CHAT_EXPANDED_INSET = 380;

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
  const [chatTarget, setChatTarget] = useState<ChatTarget | null>(null);
  const [chatExpanded, setChatExpanded] = useState(false);
  // 선택된 에이전트: 왼쪽 카드와 구성도 노드가 함께 강조된다. focusSeq 가 오를 때마다 구성도가 그쪽으로 이동한다.
  const [selectedAgentId, setSelectedAgentId] = useState<number | null>(null);
  const [focusSeq, setFocusSeq] = useState(0);

  const project = projects.find((p) => String(p.id) === id);
  const masterAgentId = project?.masterAgentId ?? null;

  useEffect(() => {
    if (notice === null) return;
    const timer = window.setTimeout(() => setNotice(null), NOTICE_MS);
    return () => window.clearTimeout(timer);
  }, [notice]);

  // 채팅 대상은 마스터가 기본이다(원하면 그룹/에이전트를 직접 고를 수 있다).
  useEffect(() => {
    if (masterAgentId === null) return;
    setChatTarget((prev) => prev ?? { kind: 'agent', id: masterAgentId });
  }, [masterAgentId]);

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

  // 삭제된 에이전트가 선택된 채로 남지 않게 실제로 있는 에이전트만 선택으로 인정한다.
  const activeAgentId = agents.some((a) => a.id === selectedAgentId && a.projectId === project.id) ? selectedAgentId : null;

  /**
   * 에이전트를 선택하고 채팅 대상으로도 삼는다. 왼쪽 카드에서 누르면 구성도도 그쪽으로 이동한다.
   * 이미 선택된 에이전트를 다시 누르면 선택이 풀린다(채팅 대상이 그 에이전트였다면 대상도 비운다).
   */
  const selectAgent = (agentId: number, focus: boolean) => {
    if (agentId === activeAgentId) {
      setSelectedAgentId(null);
      setChatTarget((prev) => (prev?.kind === 'agent' && prev.id === agentId ? null : prev));
      return;
    }
    setSelectedAgentId(agentId);
    setChatTarget({ kind: 'agent', id: agentId });
    if (focus) setFocusSeq((prev) => prev + 1);
  };

  const defaultWorkspace = project.workspaces.find((w) => w.isDefault);
  const workspaceName = workspaces.find((w) => w.id === defaultWorkspace?.workspaceId)?.name;
  const extra = project.workspaces.length - 1;
  const agentCount = agents.filter((a) => a.projectId === project.id).length;
  const groupCount = groups.filter((g) => g.projectId === project.id).length;
  const stats = [
    { label: '에이전트', value: agentCount },
    { label: '그룹', value: groupCount },
    { label: '총 Task', value: tasks.filter((t) => t.projectId === project.id).length },
  ];

  return (
    <div
      className={styles.stage}
      style={{ '--inset': `${agentsOpen ? AGENT_PANEL_INSET : NO_PANEL_INSET}px` } as CSSProperties}
    >
      <GroupCanvas
        project={project}
        insetLeft={agentsOpen ? AGENT_PANEL_INSET : NO_PANEL_INSET}
        insetBottom={chatExpanded ? CHAT_EXPANDED_INSET : CHAT_COLLAPSED_INSET}
        selectedAgentId={activeAgentId}
        focusSeq={focusSeq}
        onSelectAgent={(agentId) => selectAgent(agentId, false)}
        onNotice={setNotice}
      />

      <div className={styles.leftColumn}>
        <button
          type="button"
          className={styles.fold}
          onClick={() => setAgentsOpen((prev) => !prev)}
          aria-expanded={agentsOpen}
          aria-label={agentsOpen ? '에이전트·그룹 패널 접기' : '에이전트·그룹 패널 펴기'}
        >
          {agentsOpen ? <ChevronUpIcon /> : <ChevronDownIcon />}
          {agentsOpen ? '접기' : '펴기'}
        </button>

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

        {agentsOpen && (
          <>
            <AgentList
              project={project}
              selectedAgentId={activeAgentId}
              onSelectAgent={(agentId) => selectAgent(agentId, true)}
            />
            <GroupList
              project={project}
              target={chatTarget}
              onSelect={(target) => {
                setChatTarget(target);
                setSelectedAgentId(null);
              }}
              onNotice={setNotice}
            />
          </>
        )}
      </div>

      <ChatPanel
        project={project}
        target={chatTarget}
        onTargetChange={setChatTarget}
        expanded={chatExpanded}
        onToggle={() => setChatExpanded((prev) => !prev)}
        onSent={() => setChatExpanded(true)}
      />

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
