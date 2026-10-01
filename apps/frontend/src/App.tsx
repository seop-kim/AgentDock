import { Navigate, Outlet, Route, Routes, useMatch } from 'react-router-dom';
import styles from './layout.module.css';
import Sidebar from './components/Sidebar';
import Dashboard from './features/dashboard/Dashboard';
import Projects from './features/project/Projects';
import ProjectDetail from './features/project/ProjectDetail';
import Groups from './features/task/Groups';
import Tasks from './features/task/Tasks';
import Providers from './features/runtime/Providers';
import Workspaces from './features/workspace/Workspaces';
import Agents from './features/agent/Agents';
import ExecutionDetail from './features/execution/ExecutionDetail';
import TerminalWindow from './features/execution/TerminalWindow';

function Layout() {
  // 프로젝트 상세는 화면 전체를 쓰므로 여백·최대폭 제한 없이 그린다.
  const fullBleed = useMatch('/projects/:id') !== null;
  return (
    <div className={styles.shell}>
      <Sidebar />
      <main className={`${styles.main} ${fullBleed ? styles.mainFull : ''}`}>
        <Outlet />
      </main>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      {/* 터미널 창은 새 창으로 뜨므로 사이드바·레이아웃 없이 그린다 */}
      <Route path="/terminal/:executionId" element={<TerminalWindow />} />
      <Route element={<Layout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/projects" element={<Projects />} />
        <Route path="/projects/:id" element={<ProjectDetail />} />
        <Route path="/groups" element={<Groups />} />
        <Route path="/tasks" element={<Tasks />} />
        <Route path="/providers" element={<Providers />} />
        <Route path="/workspaces" element={<Workspaces />} />
        <Route path="/agents" element={<Agents />} />
        <Route path="/executions/:id" element={<ExecutionDetail />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
