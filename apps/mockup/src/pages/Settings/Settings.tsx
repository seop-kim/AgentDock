import { NavLink, Outlet } from 'react-router-dom';
import styles from './Settings.module.css';

const SECTIONS = [
  { to: '/settings/agents', label: '에이전트 연결 설정' },
  { to: '/settings/theme', label: '테마' },
];

/** 설정 화면의 틀. 안쪽 왼쪽 메뉴로 설정 항목(에이전트 연결 설정, 테마)을 구분한다. */
export default function Settings() {
  return (
    <div className={styles.layout}>
      <nav className={styles.menu} aria-label="설정 항목">
        <p className={styles.menuTitle}>설정</p>
        {SECTIONS.map(({ to, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => `${styles.menuItem} ${isActive ? styles.menuItemActive : ''}`}
          >
            {label}
          </NavLink>
        ))}
      </nav>
      <section className={styles.content}>
        <Outlet />
      </section>
    </div>
  );
}
