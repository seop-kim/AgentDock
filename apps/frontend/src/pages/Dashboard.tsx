import { Link } from 'react-router-dom';
import styles from './Dashboard.module.css';

export default function Dashboard() {
  return (
    <div>
      <h1>AgentDock</h1>
      <p>멀티 에이전트 조직 운영 플랫폼 — Phase 2 (Project/Group/Task)</p>
      <ul className={styles.list}>
        <li><Link to="/providers">AI Provider 등록</Link></li>
        <li><Link to="/workspaces">Workspace 등록</Link></li>
        <li><Link to="/projects">Project 생성 (워크스페이스 지정)</Link></li>
        <li><Link to="/agents">Agent 생성</Link></li>
        <li><Link to="/groups">Group 구성 (리더/멤버)</Link></li>
        <li><Link to="/tasks">Task 생성 및 실행</Link></li>
      </ul>
    </div>
  );
}
