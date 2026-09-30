import { Navigate, Outlet, Route, Routes, useMatch } from 'react-router-dom';
import styles from './layout.module.css';
import Sidebar from './components/Sidebar';
import Dashboard from './pages/Dashboard';
import ProjectDetail from './pages/Projects/ProjectDetail';
import Projects from './pages/Projects/Projects';
import AgentConnectionSettings from './pages/Settings/AgentConnectionSettings';
import Settings from './pages/Settings/Settings';
import ThemeSettings from './pages/Settings/ThemeSettings';

function Layout() {
  // 프로젝트 상세는 구성도가 화면 전체를 차지하므로 여백 없이 쓴다.
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
      <Route element={<Layout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/projects" element={<Projects />} />
        <Route path="/projects/:id" element={<ProjectDetail />} />
        <Route path="/settings" element={<Settings />}>
          <Route index element={<Navigate to="agents" replace />} />
          <Route path="agents" element={<AgentConnectionSettings />} />
          <Route path="theme" element={<ThemeSettings />} />
        </Route>
      </Route>
    </Routes>
  );
}
