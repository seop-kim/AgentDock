import { Link } from 'react-router-dom';
import styles from './Dashboard.module.css';

export default function Dashboard() {
  return (
    <div>
      <h1>AgentDock</h1>
      <p>멀티 에이전트 조직 운영 플랫폼 — 목업</p>
      <ul className={styles.list}>
        <li>
          <Link to="/settings">AI 런타임 설정</Link>
        </li>
        <li className={styles.pending}>Workspace 등록 (준비 중)</li>
        <li>
          <Link to="/projects">Project 생성 (워크스페이스 지정)</Link>
        </li>
        <li className={styles.pending}>Agent 생성 (준비 중)</li>
        <li className={styles.pending}>Group 구성 (준비 중)</li>
        <li className={styles.pending}>Task 생성 및 실행 (준비 중)</li>
        <li>
          <Link to="/reality-check">현실성 점검 (실제 구현 시 막히는 지점)</Link>
        </li>
      </ul>
    </div>
  );
}
