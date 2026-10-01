import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import TerminalView from '../components/TerminalView';
import { currentExecution, currentTask, terminalState } from '../lib/terminal';
import { readSnapshot, subscribeSnapshot, type MockSnapshot } from '../lib/windowSync';
import styles from './TerminalWindow.module.css';

/**
 * 새 창으로 띄운 에이전트 터미널. 메인 창이 넘겨준 상태를 받아 그 에이전트의 실행 출력만 보여 준다.
 * 실제 구현에서는 이 창이 그 실행의 로그 스트림(SSE)을 직접 구독하면 되고, 넘겨주는 다리는 필요 없다.
 */
export default function TerminalWindow() {
  const { agentId } = useParams();
  const [snapshot, setSnapshot] = useState<MockSnapshot | null>(() => readSnapshot());
  useEffect(() => subscribeSnapshot(setSnapshot), []);

  const agent = snapshot?.agents.find((a) => a.id === Number(agentId)) ?? null;
  if (!snapshot || !agent) {
    return (
      <div className={styles.page}>
        <p className={styles.empty}>에이전트를 찾을 수 없습니다. 메인 창에서 다시 열어 주세요.</p>
      </div>
    );
  }

  const project = snapshot.projects.find((p) => p.id === agent.projectId);
  const defaultWorkspaceId = project?.workspaces.find((w) => w.isDefault)?.workspaceId;
  const cwd = snapshot.workspaces.find((w) => w.id === defaultWorkspaceId)?.path ?? null;
  const execution = currentExecution(
    snapshot.executions.filter((e) => e.projectId === agent.projectId),
    agent.id,
  );
  const task = currentTask(
    snapshot.tasks.filter((t) => t.projectId === agent.projectId),
    agent.id,
  );
  const state = terminalState(execution, task);

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>{agent.name} 터미널</h1>
        <span className={styles.state}>
          {state === 'live' ? '실행 중' : state === 'done' ? '완료' : '작업 없음'}
        </span>
      </header>
      <p className={styles.cwd}>{cwd ?? '워크스페이스가 없습니다'}</p>
      <TerminalView
        agent={agent}
        execution={execution}
        task={task}
        groups={snapshot.groups.filter((g) => g.projectId === agent.projectId)}
        cwd={cwd}
        className={styles.terminal}
      />
      <p className={styles.note}>모의 출력입니다. 실제 구현에서는 이 창이 실행 로그(SSE)를 직접 구독합니다.</p>
    </div>
  );
}
