import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { formatCost, formatTokens, isLive } from '../lib/executions';
import { useAgentDockStore } from '../store/AgentDockStore';
import styles from './Dashboard.module.css';

/**
 * 홈. 할 수 있는 일과 **지금까지 쓴 것**(실행·비용·토큰)을 한눈에 보여 준다.
 * 값은 **서버가 준 프로젝트 전체 실행 목록**(`GET /executions`)의 계측값을 더한 것이라 전체가 정확하다 —
 * 목록을 못 읽으면(구버전) 지금 화면이 들고 있는 실행으로 내려간다.
 */
export default function Dashboard() {
  const { projects, groups, agents, executions, executionList, loadExecutionList } = useAgentDockStore();

  // 전체 사용량은 화면 조각이 아니라 서버 실행 목록에서 계산한다(첫 로드·전역 이벤트 갱신 때).
  useEffect(() => {
    loadExecutionList({ limit: 500 });
  }, [loadExecutionList]);

  const all = executionList.length > 0 ? executionList : executions;
  const costOf = (list: typeof all) => list.reduce((sum, execution) => sum + (execution.metrics?.costUsd ?? 0), 0);
  const totalCost = costOf(all);
  const inputTokens = all.reduce((sum, execution) => sum + (execution.metrics?.inputTokens ?? 0), 0);
  const outputTokens = all.reduce((sum, execution) => sum + (execution.metrics?.outputTokens ?? 0), 0);
  const running = all.filter((execution) => isLive(execution.status)).length;

  // 프로젝트별 / 팀별 사용량(많이 쓴 순). 실행이 없는 줄은 숨긴다.
  const byProject = projects
    .map((project) => {
      const mine = all.filter((execution) => execution.projectId === project.id);
      return { id: project.id, name: project.name, count: mine.length, cost: costOf(mine) };
    })
    .filter((row) => row.count > 0)
    .sort((a, b) => b.cost - a.cost);
  const byTeam = groups
    .map((group) => {
      const memberIds = new Set([
        ...group.memberIds,
        ...(group.leaderAgentId === null ? [] : [group.leaderAgentId]),
      ]);
      const mine = all.filter((execution) => memberIds.has(execution.agentId));
      return { id: group.id, name: group.name, count: mine.length, cost: costOf(mine) };
    })
    .filter((row) => row.count > 0)
    .sort((a, b) => b.cost - a.cost);

  return (
    <div>
      <h1>AgentDock</h1>
      <p>멀티 에이전트 조직 운영 플랫폼</p>

      <h2>사용량</h2>
      <ul className={styles.list}>
        <li>
          실행 {all.length}건 (지금 도는 것 {running}건) · 비용 {formatCost(totalCost)} · 토큰 입력{' '}
          {formatTokens(inputTokens)} / 출력 {formatTokens(outputTokens)}
        </li>
        {byProject.slice(0, 5).map((row) => (
          <li key={`p-${row.id}`}>
            <Link to={`/projects/${row.id}`}>{row.name}</Link> — {row.count}건 · {formatCost(row.cost)}
          </li>
        ))}
        {byTeam.slice(0, 8).map((row) => (
          <li key={`g-${row.id}`}>
            {row.name} 팀 — {row.count}건 · {formatCost(row.cost)}
          </li>
        ))}
      </ul>

      <h2>할 수 있는 일</h2>
      <ul className={styles.list}>
        <li>
          <Link to="/projects">프로젝트 (에이전트·팀 구성과 명령 실행)</Link>
        </li>
        <li>
          <Link to="/tasks">Tasks (전 프로젝트 작업·실행 목록)</Link>
        </li>
        <li>
          <Link to="/settings">AI 런타임 설정 (설치·로그인·모델)</Link>
        </li>
        <li>
          <Link to="/reality-check">현실성 점검 (실제 구현 시 막히는 지점)</Link>
        </li>
      </ul>

      <p>
        에이전트·팀 만들기는 프로젝트 상세 안에서 합니다(등록된 에이전트 {agents.length}명, 팀 {groups.length}개).
      </p>
    </div>
  );
}
