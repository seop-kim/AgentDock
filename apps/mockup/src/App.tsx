import { Route, Routes, Link, Outlet } from 'react-router-dom';
import styles from './layout.module.css';
import ThemeToggle from './components/ThemeToggle';
import Dashboard from './pages/Dashboard';
import Settings from './pages/Settings/Settings';

function Layout() {
  return (
    <div>
      <nav className={styles.nav}>
        <Link to="/">Dashboard</Link>
        <Link to="/settings">설정</Link>
        <div className={styles.spacer} />
        <ThemeToggle />
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
        <Route path="/settings" element={<Settings />} />
      </Route>
    </Routes>
  );
}
