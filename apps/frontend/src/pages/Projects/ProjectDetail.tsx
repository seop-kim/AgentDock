import { CSSProperties, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { SettingsIcon } from '../../components/icons';
import { useAgentDockStore } from '../../store/AgentDockStore';
import shared from '../../styles/shared.module.css';
import { questionText } from '../../lib/executions';
import { toast } from '../../components/Toaster';
import type { ChatTarget } from '../../types';
import AgentList from './AgentList';
import ChatPanel from './ChatPanel';
import ExecutionTreeModal from './ExecutionTree';
import GroupCanvas from './GroupCanvas';
import GroupList from './GroupList';
import styles from './ProjectDetail.module.css';
import ProjectSettingsModal from './ProjectSettingsModal';

const NOTICE_MS = 2500;
/** 에이전트 패널 너비(320) + 왼쪽 여백(16) + 호흡(16). 구성도 맞춤이 이만큼을 비켜 간다. */
const AGENT_PANEL_INSET = 352;
/** 명령 패널 너비(360) + 오른쪽 여백(16) + 호흡(16). */
const CHAT_PANEL_INSET = 392;
const NO_PANEL_INSET = 24;
/** 아래쪽 호흡 */
const BOTTOM_INSET = 24;

/**
 * 프로젝트 상세. 구성도가 화면 전체를 채우고, 헤더/에이전트 패널/설정 버튼/도구 막대가 그 위에 떠 있다.
 */
export default function ProjectDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { projects, workspaces, agents, groups, tasks, executions, reload, watchExecutionTree } =
    useAgentDockStore();
  const [notice, setNotice] = useState<string | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  /** 실행 트리 창에 띄울 실행(채팅 응답에 딸린 루트 실행 id). */
  const [executionTreeRoot, setExecutionTreeRoot] = useState<number | null>(null);
  // 에이전트 패널과 그룹 패널은 따로 접고 펼 수 있다(기본은 둘 다 열림).
  const [agentsOpen, setAgentsOpen] = useState(true);
  const [groupsOpen, setGroupsOpen] = useState(true);
  const [chatTarget, setChatTarget] = useState<ChatTarget | null>(null);
  /** 오른쪽 명령 패널을 펼쳐 두었는지(기본 열림). 접으면 머리말만 남는다. */
  const [chatOpen, setChatOpen] = useState(true);
  /** 명령 패널을 크게 보는 중인지(일시적으로 구성도를 덮는다). */
  const [chatWide, setChatWide] = useState(false);
  // 선택된 에이전트: 왼쪽 카드와 구성도 노드가 함께 강조된다. focusSeq 가 오를 때마다 구성도가 그쪽으로 이동한다.
  const [selectedAgentId, setSelectedAgentId] = useState<number | null>(null);
  const [focusSeq, setFocusSeq] = useState(0);
  /** 구성도에서 노드를 눌렀을 때 값이 오른다: 왼쪽 에이전트 목록이 그 카드로 스크롤한다(반대 방향). */
  const [cardFocusSeq, setCardFocusSeq] = useState(0);

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

  // 실행 트리 창을 열어 둔 동안에는 그 트리만 계속 다시 읽는다.
  // 나머지 갱신은 전역 이벤트 스트림(SSE)이 몰고 간다 — 주기 폴링은 두지 않는다.
  const projectId = project?.id ?? null;
  useEffect(() => {
    if (projectId === null) return;
    watchExecutionTree(projectId, executionTreeRoot);
    return () => watchExecutionTree(projectId, null);
  }, [projectId, executionTreeRoot, watchExecutionTree]);

  // 입력 대기가 새로 생기면 브라우저 알림으로 알린다 — 다른 탭을 보고 있어도 답할 때를 알게.
  // 첫 로드(이미 대기 중이던 것)에서는 알리지 않고, 새로 생긴 순간에만 알린다.
  const waitingKey = executions
    .filter((execution) => execution.projectId === project?.id && execution.status === 'WAITING_INPUT')
    .map((execution) => execution.id)
    .join(',');
  const askedRef = useRef<string | null>(null);
  useEffect(() => {
    const previous = askedRef.current;
    askedRef.current = waitingKey;
    if (previous === null || previous === waitingKey || waitingKey === '' || typeof Notification === 'undefined') {
      return;
    }
    if (Notification.permission === 'default') {
      void Notification.requestPermission();
      return;
    }
    if (Notification.permission !== 'granted') return;
    const asked = executions.filter(
      (execution) => execution.projectId === project?.id && execution.status === 'WAITING_INPUT',
    );
    const first = asked[asked.length - 1];
    const notification = new Notification(`입력 대기 — ${project?.name ?? '프로젝트'}`, {
      body: first === undefined ? '에이전트가 사람의 답을 기다립니다.' : questionText(first),
    });
    notification.onclick = () => window.focus();
  }, [waitingKey, executions, project?.id, project?.name]);

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

  // 둘 다 접으면 왼쪽 패널이 차지하는 폭이 없어져 구성도가 화면 전체를 쓴다.
  const panelInset = agentsOpen || groupsOpen ? AGENT_PANEL_INSET : NO_PANEL_INSET;
  const chatInset = chatOpen ? CHAT_PANEL_INSET : NO_PANEL_INSET;

  return (
    <div className={styles.stage} style={{ '--left-inset': `${panelInset}px` } as CSSProperties}>
      <GroupCanvas
        project={project}
        insetLeft={panelInset}
        insetRight={chatInset}
        insetBottom={BOTTOM_INSET}
        selectedAgentId={activeAgentId}
        focusSeq={focusSeq}
        // 구성도에서 고른 에이전트를 왼쪽 목록에서도 찾을 수 있게, 카드가 보이도록 스크롤시킨다(반대 방향).
        onSelectAgent={(agentId) => {
          if (agentId !== activeAgentId) {
            setAgentsOpen(true);
            setCardFocusSeq((prev) => prev + 1);
          }
          selectAgent(agentId, false);
        }}
        onNotice={setNotice}
      />

      <div className={styles.leftColumn}>
        <header className={styles.header}>
          <Link to="/projects" className={styles.back}>
            ← 프로젝트 목록
          </Link>
          <div className={styles.titleRow}>
            <h1 className={styles.title}>{project.name}</h1>
            {/* 새로고침·설정은 한 묶음으로 붙여 둔다(space-between 이 가운데로 벌리지 않게). */}
            <div className={styles.titleActions}>
              <button
                type="button"
                className={styles.settingsButton}
                onClick={() => {
                // 다시 읽은 뒤 알려 준다(눌렀는데 아무 반응이 없어 보이지 않게).
                void reload().then(() => toast('서버에서 다시 읽었습니다 — 캔버스·에이전트·명령을 갱신했습니다'));
              }}
                aria-label="새로고침"
                title="캔버스·에이전트 목록·명령을 서버에서 다시 읽습니다"
              >
                ⟳
              </button>
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

        {/* 입력 대기 알약은 왼쪽이 아니라 **구성도 아래 가운데**에 뜬다(GroupCanvas 가 확대/축소 도구 위에 그린다). */}
        <AgentList
          project={project}
          selectedAgentId={activeAgentId}
          focusSeq={cardFocusSeq}
          onSelectAgent={(agentId) => selectAgent(agentId, true)}
          open={agentsOpen}
          onToggle={() => setAgentsOpen((prev) => !prev)}
        />
        <GroupList
          project={project}
          target={chatTarget}
          onSelect={(target) => {
            setChatTarget(target);
            setSelectedAgentId(null);
          }}
          onNotice={setNotice}
          open={groupsOpen}
          onToggle={() => setGroupsOpen((prev) => !prev)}
        />
      </div>

      <ChatPanel
        project={project}
        target={chatTarget}
        onTargetChange={setChatTarget}
        open={chatOpen}
        // 접으면 크게 보기도 함께 푼다(넓고 얇은 띠가 남지 않게).
        onToggle={() => {
          setChatOpen((prev) => !prev);
          setChatWide(false);
        }}
        wide={chatWide}
        onToggleWide={() => setChatWide((prev) => !prev)}
        onOpenExecutions={setExecutionTreeRoot}
      />

      {notice && <p className={`errorText ${styles.notice}`}>{notice}</p>}

      {executionTreeRoot !== null && (
        <ExecutionTreeModal
          executions={executions.filter((e) => e.projectId === project.id)}
          rootExecutionId={executionTreeRoot}
          masterAgentId={masterAgentId}
          onClose={() => setExecutionTreeRoot(null)}
        />
      )}

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
