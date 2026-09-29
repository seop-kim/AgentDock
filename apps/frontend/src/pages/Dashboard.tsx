import styles from './Dashboard.module.css';

export default function Dashboard() {
  return (
    <div>
      <h1>AgentDock</h1>
      <p>멀티 에이전트 조직 운영 플랫폼 — Phase 1 MVP</p>
      <ul className={styles.list}>
        <li><a href="/providers">AI Provider 등록</a></li>
        <li><a href="/workspaces">Workspace 등록</a></li>
        <li><a href="/agents">Agent 생성 및 실행</a></li>
      </ul>
    </div>
  );
}
