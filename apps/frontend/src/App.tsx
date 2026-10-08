import { Navigate, Outlet, Route, Routes, useMatch } from 'react-router-dom';
import styles from './layout.module.css';
import AppStatus from './components/AppStatus';
import ConfirmDialog from './components/ConfirmDialog';
import ServerErrorDialog from './components/ServerErrorDialog';
import Sidebar from './components/Sidebar';
import Toaster from './components/Toaster';
import Dashboard from './pages/Dashboard';
import ProjectDetail from './pages/Projects/ProjectDetail';
import Projects from './pages/Projects/Projects';
import RealityCheck from './pages/RealityCheck';
import TerminalWindow from './pages/TerminalWindow';
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
    <>
      <Routes>
        {/* 터미널 창은 새 창으로 뜨므로 사이드바·레이아웃 없이 그린다 */}
        <Route path="/terminal/:agentId" element={<TerminalWindow />} />
        <Route element={<Layout />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/projects" element={<Projects />} />
          <Route path="/projects/:id" element={<ProjectDetail />} />
          <Route path="/reality-check" element={<RealityCheck />} />
          <Route path="/settings" element={<Settings />}>
            <Route index element={<Navigate to="agents" replace />} />
            <Route path="agents" element={<AgentConnectionSettings />} />
            <Route path="theme" element={<ThemeSettings />} />
          </Route>
        </Route>
      </Routes>
      {/* 첫 로딩·서버 오류 표시는 화면 어디서든 뜨도록 여기서 한 번만 그린다. */}
      <AppStatus />
      {/* 작업 알림(토스트)과 확인 창도 여기서 한 번만 그린다. */}
      <Toaster />
      <ConfirmDialog />
      {/* 서버 내부 오류(5xx)는 원인을 보여 주고 에이전트에게 수정을 맡길지 고르게 한다. */}
      <ServerErrorDialog />
    </>
  );
}
