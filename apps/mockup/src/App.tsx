import { Navigate, Route, Routes, Outlet } from 'react-router-dom';
import styles from './layout.module.css';
import Sidebar from './components/Sidebar';
import Dashboard from './pages/Dashboard';
import Projects from './pages/Projects/Projects';
import AgentConnectionSettings from './pages/Settings/AgentConnectionSettings';
import Settings from './pages/Settings/Settings';
import ThemeSettings from './pages/Settings/ThemeSettings';

function Layout() {
  return (
    <div className={styles.shell}>
      <Sidebar />
      <main className={styles.main}>
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
        <Route path="/settings" element={<Settings />}>
          <Route index element={<Navigate to="agents" replace />} />
          <Route path="agents" element={<AgentConnectionSettings />} />
          <Route path="theme" element={<ThemeSettings />} />
        </Route>
      </Route>
    </Routes>
  );
}
