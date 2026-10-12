import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import TerminalView from '../components/TerminalView';
import { CancelButton, useCancelNotice } from './Projects/ExecutionTree';
import { currentExecution, currentTask, terminalState } from '../lib/terminal';
import { useAgentDockStore } from '../store/AgentDockStore';
import styles from './TerminalWindow.module.css';

/**
 * 새 창으로 띄운 에이전트 터미널(`/terminal/:agentId`).
 * 스토어에서 그 에이전트의 **현재 실행**을 찾아(`currentExecution`/`currentTask`), 그 실행의 실제 로그를
 * 이 창이 직접 SSE 로 구독해 보여 준다(메인 창이 넘겨주는 스냅샷 다리는 없다).
 * 이 창은 **항상 살아 있다** — 스토어가 전역 이벤트 스트림(SSE)으로 갱신되고(끊기면 느린 안전망 폴링이 따라온다),
 * 새 실행으로 넘어가면 터미널도 그 실행의 로그 스트림으로 갈아탄다.
 */
export default function TerminalWindow() {
  const { agentId } = useParams();
  const { agents, projects, workspaces, tasks, executions, executionList, loadExecutionList, loading, watchExecutionTree } =
    useAgentDockStore();
  const [notice, setNotice] = useCancelNotice();
  /** 보고 있는 실행. null 이면 **현재** 실행, 값이 있으면 그 **과거** 실행의 기록을 본다. */
  const [historyId, setHistoryId] = useState<number | null>(null);

  const agent = agents.find((candidate) => candidate.id === Number(agentId)) ?? null;
  const agentProjectId = agent?.projectId ?? null;

  /*
   * 이 창은 프로젝트 상세 **밖**(별도 라우트)에서 열리므로 상세 화면이 slice 를 읽어 주지 않는다.
   * 그래서 터미널이 자기 에이전트의 프로젝트를 직접 열어 달라고 알린다 — 안 하면 실행 목록이 비어
   * "작업 없음" 으로 보인다(2026-10-08 실측). 스토어는 같은 프로젝트면 다시 읽지 않으므로 중복 호출은 없다.
   */
  useEffect(() => {
    if (agentProjectId !== null) watchExecutionTree(agentProjectId, null);
  }, [agentProjectId, watchExecutionTree]);

  // 지난 실행 목록(무엇을 언제 했나) — 이 창에서 직접 읽는다. 고르면 그 로그를 정적으로 본다.
  useEffect(() => {
    if (agentProjectId !== null) loadExecutionList({ projectId: agentProjectId, limit: 200 });
  }, [agentProjectId, loadExecutionList]);

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
  // 이 에이전트의 지난 실행(최근 순). 현재 실행도 목록에 있으므로 "현재"와 겹치지 않게 위에서 고른다.
  const pastExecutions = executionList
    .filter((candidate) => candidate.agentId === agent.id)
    .sort((left, right) => right.id - left.id);
  const isHistory = historyId !== null;
  const shown = isHistory ? (pastExecutions.find((candidate) => candidate.id === historyId) ?? null) : execution;
  const historyLabel = (status: string) =>
    status === 'DONE' ? '완료' : status === 'FAILED' ? '실패' : status === 'CANCELLED' ? '중단' : '진행 중';
  // 격리된 실행은 워크스페이스가 아니라 worktree 안에서 돈다 — 실제 작업 디렉터리를 보여 준다.
  const runDir = execution?.worktreePath ?? cwd;

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>{agent.name} 터미널</h1>
        <span className={styles.state}>
          {isHistory ? '기록' : state === 'live' ? '실행 중' : state === 'done' ? '완료' : '작업 없음'}
        </span>
        {/* 지난 실행 고르기 — 고르면 그 로그를 정적으로 보고, "현재"로 돌아올 수 있다. */}
        {pastExecutions.length > 0 && (
          <select
            className={styles.state}
            value={historyId === null ? '' : String(historyId)}
            onChange={(event) => setHistoryId(event.target.value === '' ? null : Number(event.target.value))}
            aria-label="실행 이력"
            title="지난 실행의 로그를 봅니다"
          >
            <option value="">현재{execution ? ` (#${execution.id})` : ''}</option>
            {pastExecutions.map((past) => (
              <option key={past.id} value={past.id}>
                #{past.id} {historyLabel(past.status)}
              </option>
            ))}
          </select>
        )}
        {execution?.worktreeBranch && (
          <span className={styles.state} title={execution.worktreePath ?? undefined}>
            워크트리 {execution.worktreeBranch}
          </span>
        )}
        {execution?.resultCommit && (
          <span className={styles.state} title={execution.mergeDetail ?? undefined}>
            커밋 {execution.resultCommit}
          </span>
        )}
        {/* 과거 기록을 보는 중에는 취소 버튼을 두지 않는다(그 실행은 이미 끝났다 — 지금 실행을 멈추는 버튼으로 오해하게 된다). */}
        {!isHistory && execution && <CancelButton execution={execution} name={agent.name} onError={setNotice} />}
      </header>
      {notice && <p className="errorText">{notice}</p>}
      <p className={styles.cwd}>{runDir ?? '워크스페이스가 없습니다'}</p>
      <TerminalView
        executionId={shown?.id ?? null}
        live={!isHistory && state === 'live'}
        className={styles.terminal}
        filterable
        history={isHistory}
      />
    </div>
  );
}
