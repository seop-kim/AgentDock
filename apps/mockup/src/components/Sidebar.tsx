import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { readStorage, STORAGE_KEYS, writeStorage } from '../lib/storage';
import { DashboardIcon, MenuIcon, SettingsIcon } from './icons';
import styles from './Sidebar.module.css';

const MENUS = [
  { to: '/', label: 'Dashboard', Icon: DashboardIcon },
  { to: '/settings', label: '설정', Icon: SettingsIcon },
];

/**
 * 왼쪽 메뉴. 기본은 아이콘만 보이고, 위의 메뉴 버튼을 누르면 이름까지 펼쳐지며 다시 누르면 접힌다.
 * 펼침 여부는 localStorage 에 저장해 다시 열어도 유지한다.
 */
export default function Sidebar() {
  const [expanded, setExpanded] = useState(() => readStorage(STORAGE_KEYS.sidebarExpanded) === 'true');

  const toggle = () => {
    const next = !expanded;
    setExpanded(next);
    writeStorage(STORAGE_KEYS.sidebarExpanded, String(next));
  };

  return (
    <aside className={`${styles.sidebar} ${expanded ? styles.expanded : ''}`}>
      <button
        type="button"
        className={styles.item}
        onClick={toggle}
        aria-expanded={expanded}
        aria-label={expanded ? '메뉴 접기' : '메뉴 펼치기'}
        title={expanded ? undefined : '메뉴 펼치기'}
      >
        <span className={styles.icon}>
          <MenuIcon />
        </span>
        {expanded && <span className={styles.brand}>AgentDock</span>}
      </button>

      <nav className={styles.menus}>
        {MENUS.map(({ to, label, Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) => `${styles.item} ${isActive ? styles.active : ''}`}
            title={expanded ? undefined : label}
            aria-label={label}
          >
            <span className={styles.icon}>
              <Icon />
            </span>
            {expanded && <span className={styles.label}>{label}</span>}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
