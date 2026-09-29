import { Route, Routes, Link, Outlet } from 'react-router-dom';
import styles from './layout.module.css';
import Dashboard from './pages/Dashboard';
import Providers from './pages/Providers';
import Workspaces from './pages/Workspaces/Workspaces';
import Agents from './pages/Agents';
import ExecutionDetail from './pages/ExecutionDetail';

function Layout() {
  return (
    <div>
      <nav className={styles.nav}>
        <Link to="/">Dashboard</Link>
        <Link to="/agents">Agents</Link>
        <Link to="/workspaces">Workspaces</Link>
        <Link to="/providers">AI Providers</Link>
      </nav>
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
        <Route path="/providers" element={<Providers />} />
        <Route path="/workspaces" element={<Workspaces />} />
        <Route path="/agents" element={<Agents />} />
        <Route path="/executions/:id" element={<ExecutionDetail />} />
      </Route>
    </Routes>
  );
}
