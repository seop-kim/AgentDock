import { useParams } from 'react-router-dom';
import TerminalView from '../components/TerminalView';
import { currentExecution, currentTask, terminalState } from '../lib/terminal';
import { useAgentDockStore } from '../store/AgentDockStore';
import styles from './TerminalWindow.module.css';

/**
 * 새 창으로 띄운 에이전트 터미널(`/terminal/:agentId`).
 * 스토어에서 그 에이전트의 **현재 실행**을 찾아(`currentExecution`/`currentTask`), 그 실행의 실제 로그를
 * 이 창이 직접 SSE 로 구독해 보여 준다(메인 창이 넘겨주는 스냅샷 다리는 없다).
 */
export default function TerminalWindow() {
  const { agentId } = useParams();
  const { agents, projects, workspaces, tasks, executions, loading } = useAgentDockStore();

  const agent = agents.find((candidate) => candidate.id === Number(agentId)) ?? null;
  if (!agent) {
    return (
      <div className={styles.page}>
        <p className={styles.empty}>
          {loading ? '불러오는 중…' : '에이전트를 찾을 수 없습니다. 메인 창에서 다시 열어 주세요.'}
        </p>
      </div>
    );
  }

  const project = projects.find((p) => p.id === agent.projectId);
  const defaultWorkspaceId = project?.workspaces.find((w) => w.isDefault)?.workspaceId;
  const cwd = workspaces.find((w) => w.id === defaultWorkspaceId)?.path ?? null;
  const execution = currentExecution(
    executions.filter((e) => e.projectId === agent.projectId),
    agent.id,
  );
  const task = currentTask(
    tasks.filter((t) => t.projectId === agent.projectId),
    agent.id,
  );
  const state = terminalState(execution, task);
  // 격리된 실행은 워크스페이스가 아니라 worktree 안에서 돈다 — 실제 작업 디렉터리를 보여 준다.
  const runDir = execution?.worktreePath ?? cwd;

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>{agent.name} 터미널</h1>
        <span className={styles.state}>
          {state === 'live' ? '실행 중' : state === 'done' ? '완료' : '작업 없음'}
        </span>
        {execution?.worktreeBranch && (
          <span className={styles.state} title={execution.worktreePath ?? undefined}>
            워크트리 {execution.worktreeBranch}
          </span>
        )}
      </header>
      <p className={styles.cwd}>{runDir ?? '워크스페이스가 없습니다'}</p>
      <TerminalView executionId={execution?.id ?? null} live={state === 'live'} className={styles.terminal} />
    </div>
  );
}
