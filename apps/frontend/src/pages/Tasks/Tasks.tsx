import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type Task as ApiTask } from '../../lib/api';
import { STATUS_LABEL, STATUS_TONE } from '../../lib/executions';
import { useAgentDockStore } from '../../store/AgentDockStore';
import shared from '../../styles/shared.module.css';
import styles from './Tasks.module.css';

type Filter = 'all' | 'active' | 'done';

/** 백엔드 Task 상태 → 화면 표시(진행/종료 구분). */
const TASK_LABEL: Record<string, string> = {
  CREATED: '대기',
  IN_PROGRESS: '진행 중',
  SUCCEEDED: '완료',
  FAILED: '실패',
};
const isActive = (status: string) => status === 'CREATED' || status === 'IN_PROGRESS';

/**
 * Tasks — 전 프로젝트의 작업(명령)과 최근 실행을 한자리에서 본다.
 * 작업은 `GET /tasks`, 실행은 서버 실행 목록(`GET /executions`)에서 온다(화면이 들고 있는 조각만 보지 않는다).
 */
export default function Tasks() {
  const { projects, agents, loadExecutionList, executionList, loading } = useAgentDockStore();
  const [tasks, setTasks] = useState<ApiTask[]>([]);
  const [filter, setFilter] = useState<Filter>('all');
  const [error, setError] = useState<string | null>(null);

  // 실행 목록은 스토어가 한 번만 읽어 두고, 전역 이벤트가 갱신할 때 다시 읽는다.
  useEffect(() => {
    loadExecutionList({ limit: 200 });
  }, [loadExecutionList]);

  // 작업 목록은 실행 목록이 바뀔 때(전역 이벤트) 다시 읽어 상태를 맞춘다.
  useEffect(() => {
    let alive = true;
    void api
      .listAllTasks(200)
      .then((rows) => {
        if (alive) setTasks(rows);
      })
      .catch((ex) => {
        if (alive) setError(String(ex));
      });
    return () => {
      alive = false;
    };
  }, [executionList]);

  const agentNameOf = (agentId: number) => agents.find((agent) => agent.id === agentId)?.name ?? '에이전트';

  const rows = useMemo(
    () =>
      tasks.filter((task) =>
        filter === 'all' ? true : filter === 'active' ? isActive(task.status) : !isActive(task.status),
      ),
    [tasks, filter],
  );
  const activeCount = tasks.filter((task) => isActive(task.status)).length;

  return (    <div>
      <h1>Tasks</h1>
      <p className={shared.hint}>
        전 프로젝트의 작업(명령)과 최근 실행을 한곳에서 봅니다. 실행 트리·로그는 프로젝트 상세에서 봅니다.
      </p>

      <div className={styles.toolbar}>
        {(['all', 'active', 'done'] as Filter[]).map((value) => (
          <button
            key={value}
            type="button"
            className={`${styles.filter} ${filter === value ? styles.filterOn : ''}`}
            onClick={() => setFilter(value)}
          >
            {value === 'all'
              ? `전체 ${tasks.length}`
              : value === 'active'
                ? `진행 중 ${activeCount}`
                : `종료 ${tasks.length - activeCount}`}
          </button>
        ))}
      </div>

      {loading && tasks.length === 0 && <p className={shared.muted}>불러오는 중…</p>}
      {error !== null && <p className={shared.muted}>작업 목록을 읽지 못했습니다.</p>}
      {!loading && error === null && tasks.length === 0 && <p className={shared.muted}>아직 작업이 없습니다.</p>}

      {rows.length > 0 && (
        <table className={styles.table}>
          <thead>
            <tr>
              <th>프로젝트</th>
              <th>작업</th>
              <th>담당</th>
              <th>상태</th>
              <th>실행</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((task) => (
              <tr key={task.id}>
                <td>
                  {task.project ? (
                    <Link to={`/projects/${task.project.id}`}>{task.project.name}</Link>
                  ) : (
                    <span className={shared.muted}>—</span>
                  )}
                </td>
                <td className={styles.title}>{task.title}</td>
                <td>{task.agent?.name ?? (task.group === null ? '—' : `${task.group.name} (팀)`)}</td>
                <td>
                  <span className={`${styles.taskBadge} ${isActive(task.status) ? styles.taskBadgeRun : ''}`}>
                    {TASK_LABEL[task.status] ?? task.status}
                  </span>
                </td>
                <td className={shared.muted}>
                  {task.latestExecutionId === null ? '—' : `#${task.latestExecutionId}`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}      <h2>최근 실행</h2>
      {executionList.length === 0 ? (
        <p className={shared.muted}>읽어 온 실행이 없습니다.</p>
      ) : (
        <ul className={styles.execList}>
          {executionList.slice(0, 20).map((execution) => (
            <li key={execution.id} className={styles.execRow}>
              <span className={styles.execId}>#{execution.id}</span>
              <span className={`${styles.tone} ${styles[STATUS_TONE[execution.status]]}`}>
                {STATUS_LABEL[execution.status]}
              </span>
              <span className={styles.execProject}>
                {projects.find((project) => project.id === execution.projectId)?.name ?? '—'}
              </span>
              <span className={styles.execAgent}>{agentNameOf(execution.agentId)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}