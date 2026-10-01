import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Agent, AgentGroup, Execution, Project, Task, api } from '../../lib/api';
import {
  formatCost,
  formatDuration,
  isRunning,
  statusClass,
  statusLabel,
  summaryOf,
} from '../../lib/executions';
import ExecutionTreeModal from '../execution/ExecutionTreeModal';
import executionStyles from '../../styles/execution.module.css';
import styles from './ProjectDetail.module.css';

type ChatTarget = { kind: 'master' } | { kind: 'group'; id: number } | { kind: 'agent'; id: number };

const BADGE_CLASS: Record<string, string> = {
  active: executionStyles.active,
  wait: executionStyles.wait,
  done: executionStyles.done,
  failed: executionStyles.failed,
};

/**
 * 프로젝트 상세(1차).
 *
 * 왼쪽: 프로젝트 카드(마스터·기본 워크스페이스) + 에이전트 목록 + 팀 목록.
 * 오른쪽: 명령(채팅) 패널. 보낸 명령은 Task 로, 응답은 루트 실행으로 보여 주고 실행 트리·터미널 창을 연다.
 * 구성도(캔버스)와 파일 첨부는 다음 단계에서 이 구조에 그대로 끼워 넣는다.
 */
export default function ProjectDetail() {
  const { id } = useParams();
  const projectId = Number(id);

  const [project, setProject] = useState<Project | null>(null);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [groups, setGroups] = useState<AgentGroup[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [executions, setExecutions] = useState<Record<number, Execution>>({});
  const [target, setTarget] = useState<ChatTarget>({ kind: 'master' });
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [openTree, setOpenTree] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      const [loadedProject, allAgents, groupList, taskList] = await Promise.all([
        api.getProject(projectId),
        api.listAgents(),
        api.listGroups(projectId),
        api.listTasks(projectId),
      ]);
      setProject(loadedProject);
      setAgents(allAgents.filter((agent) => agent.projectId === projectId));
      setGroups(groupList);
      setTasks([...taskList].sort((left, right) => left.id - right.id));

      const pairs = await Promise.all(
        taskList
          .filter((task) => task.latestExecutionId !== null)
          .map(async (task) => [task.id, await api.getExecution(task.latestExecutionId as number)] as const),
      );
      setExecutions(Object.fromEntries(pairs));
      setError(null);
    } catch (ex) {
      setError(String(ex));
    }
  }, [projectId]);

  useEffect(() => {
    void load();
  }, [load]);

  // 진행 중인 실행이 있으면 주기적으로 갱신한다.
  const busy =
    tasks.some((task) => task.status === 'IN_PROGRESS') ||
    Object.values(executions).some((execution) => isRunning(execution.status));
  const busyRef = useRef(false);
  useEffect(() => {
    busyRef.current = busy;
  }, [busy]);
  useEffect(() => {
    const timer = window.setInterval(() => {
      if (busyRef.current) void load();
    }, 2000);
    return () => window.clearInterval(timer);
  }, [load]);

  const masterName = project?.masterAgent?.name ?? null;
  const defaultWorkspace = useMemo(
    () => project?.workspaces.find((workspace) => workspace.isDefault) ?? project?.workspaces[0] ?? null,
    [project],
  );

  const targetValue =
    target.kind === 'master' ? 'master' : target.kind === 'group' ? `g${target.id}` : `a${target.id}`;

  const onTargetChange = (value: string) => {
    if (value === 'master') setTarget({ kind: 'master' });
    else if (value.startsWith('g')) setTarget({ kind: 'group', id: Number(value.slice(1)) });
    else setTarget({ kind: 'agent', id: Number(value.slice(1)) });
  };

  const onMasterChange = async (agentId: number) => {
    try {
      setProject(await api.setProjectMaster(projectId, { agentId, masterPrompt: project?.masterPrompt ?? '' }));
      setTarget({ kind: 'master' });
      setError(null);
    } catch (ex) {
      setError(String(ex));
    }
  };

  const send = async () => {
    const body = text.trim();
    if (body === '') return;
    setSending(true);
    try {
      await api.sendCommand(projectId, {
        text: body,
        ...(target.kind === 'group' ? { groupId: target.id } : {}),
        ...(target.kind === 'agent' ? { targetAgentId: target.id } : {}),
      });
      setText('');
      setError(null);
      void load();
    } catch (ex) {
      setError(String(ex));
    } finally {
      setSending(false);
    }
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void send();
  };

  return (
    <div className={styles.layout}>
      <section className={styles.left}>
        <div className={styles.projectCard}>
          <h1>{project?.name ?? '프로젝트'}</h1>
          <div className={styles.metaRow}>
            <span className={styles.metaLabel}>기본 폴더</span>
            <span className={styles.metaValue}>
              {defaultWorkspace === null ? '할당 없음' : `${defaultWorkspace.name} — ${defaultWorkspace.path}`}
            </span>
          </div>
          <div className={styles.metaRow}>
            <span className={styles.metaLabel}>마스터</span>
            <span className={styles.metaValue}>
              {masterName === null ? <span className={styles.warn}>지정 안 됨</span> : masterName}
            </span>
          </div>
          <div className={styles.metaRow}>
            <span className={styles.metaLabel}>마스터 변경</span>
            <select
              value={project?.masterAgent?.id ?? ''}
              onChange={(event) => void onMasterChange(Number(event.target.value))}
            >
              <option value="" disabled>
                에이전트 선택
              </option>
              {agents.map((agent) => (
                <option key={agent.id} value={agent.id}>
                  {agent.name}
                </option>
              ))}
            </select>
          </div>
          <Link className={styles.itemMeta} to="/projects">
            프로젝트 목록으로
          </Link>
        </div>

        <div className={styles.panel}>
          <h2 className={styles.panelTitle}>에이전트 {agents.length}</h2>
          {agents.length === 0 && <p className={styles.empty}>이 프로젝트에 에이전트가 없습니다.</p>}
          <ul className={styles.list}>
            {agents.map((agent) => (
              <li key={agent.id} className={styles.item}>
                <span className={styles.itemName}>{agent.name}</span>
                <span className={styles.itemMeta}>{agent.role?.name ?? '역할 없음'}</span>
                <span
                  className={`${styles.itemBadge} ${agent.available ? styles.itemBadgeOk : styles.itemBadgeOff}`}
                  title={agent.unavailableReason ?? undefined}
                >
                  {agent.available ? '실행 가능' : '실행 불가'}
                </span>
              </li>
            ))}
          </ul>
        </div>

        <div className={styles.panel}>
          <h2 className={styles.panelTitle}>팀 {groups.length}</h2>
          {groups.length === 0 && <p className={styles.empty}>팀이 없으면 마스터가 직접 처리합니다.</p>}
          <ul className={styles.list}>
            {groups.map((group) => (
              <li key={group.id} className={styles.item}>
                <span className={styles.itemName}>{group.name}</span>
                {group.leader !== null && <span className={styles.itemMeta}>리더 {group.leader.name}</span>}
                <span className={styles.itemMeta}>{group.members.map((member) => member.name).join(', ')}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className={styles.chat}>
        <header className={styles.chatHead}>
          <select value={targetValue} onChange={(event) => onTargetChange(event.target.value)}>
            <option value="master">마스터{masterName === null ? '' : ` · ${masterName}`}에게</option>
            {groups.map((group) => (
              <option key={`g${group.id}`} value={`g${group.id}`}>
                팀 · {group.name}
              </option>
            ))}
            {agents.map((agent) => (
              <option key={`a${agent.id}`} value={`a${agent.id}`}>
                에이전트 · {agent.name}
              </option>
            ))}
          </select>
          <span className={styles.chatHint}>명령 하나가 실행 트리 하나가 됩니다</span>
        </header>

        <div className={styles.messages}>
          {tasks.length === 0 && <p className={styles.empty}>아직 보낸 명령이 없습니다. 명령을 보내 보세요.</p>}
          {tasks.map((task) => {
            const execution = executions[task.id];
            return (
              <div key={task.id} className={styles.message}>
                <div className={styles.bubble}>{task.title}</div>
                {execution === undefined ? (
                  <p className={styles.empty}>실행을 준비하고 있습니다…</p>
                ) : (
                  <div className={styles.answer}>
                    <p>{summaryOf(execution.resultText) || '아직 결과가 없습니다.'}</p>
                    <div className={executionStyles.card}>
                      <div className={executionStyles.cardHead}>
                        <span className={`${executionStyles.badge} ${BADGE_CLASS[statusClass(execution.status)]}`}>
                          {statusLabel(execution.status)}
                        </span>
                        <span>실행 #{execution.id}</span>
                        <span>{formatCost(execution.costUsd)}</span>
                        <span>{formatDuration(execution.durationMs)}</span>
                        <button
                          type="button"
                          className={executionStyles.cardButton}
                          onClick={() => setOpenTree(execution.id)}
                        >
                          실행 트리
                        </button>
                        <button
                          type="button"
                          className={executionStyles.cardButton}
                          onClick={() =>
                            window.open(`/terminal/${execution.id}`, '_blank', 'width=980,height=680,noopener')
                          }
                        >
                          터미널
                        </button>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {error !== null && <div className="errorText">{error}</div>}

        <form className={styles.inputRow} onSubmit={onSubmit}>
          <textarea
            value={text}
            rows={2}
            placeholder="명령을 입력하세요. 마스터가 팀에 나눠 맡깁니다. (Enter 전송 / Shift+Enter 줄바꿈)"
            onChange={(event) => setText(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                void send();
              }
            }}
          />
          <button type="submit" disabled={sending || text.trim() === ''}>
            보내기
          </button>
        </form>
      </section>

      {openTree !== null && <ExecutionTreeModal executionId={openTree} onClose={() => setOpenTree(null)} />}
    </div>
  );
}
