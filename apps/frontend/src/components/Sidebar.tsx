import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { readStorage, STORAGE_KEYS, writeStorage } from '../lib/storage';
import { useTheme, type ThemeChoice } from '../store/ThemeContext';
import { DashboardIcon, FolderIcon, MenuIcon, SettingsIcon, TeamIcon, TerminalIcon, ThemeIcon } from './icons';
import styles from './Sidebar.module.css';

const MENUS = [
  { to: '/', label: 'Dashboard', Icon: DashboardIcon },
  { to: '/projects', label: '프로젝트', Icon: FolderIcon },
  { to: '/agents', label: '에이전트', Icon: TeamIcon },
  { to: '/executions', label: '실행', Icon: TerminalIcon },
  { to: '/providers', label: '런타임 설정', Icon: SettingsIcon },
];

const THEME_LABEL: Record<ThemeChoice, string> = { system: '시스템', light: '라이트', dark: '다크' };

/** 라이트 → 다크 → 시스템 순으로 돈다. */
function nextTheme(theme: ThemeChoice): ThemeChoice {
  if (theme === 'light') return 'dark';
  if (theme === 'dark') return 'system';
  return 'light';
}

/**
 * 왼쪽 메뉴. 기본은 아이콘만 보이고, 위의 메뉴 버튼을 누르면 이름까지 펼쳐지며 다시 누르면 접힌다.
 * 펼침 여부는 localStorage 에 저장해 다시 열어도 유지한다.
 */
export default function Sidebar() {
  const [expanded, setExpanded] = useState(() => readStorage(STORAGE_KEYS.sidebarExpanded) === 'true');
  const { theme, setTheme } = useTheme();

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

      {/* 테마는 UI 설정이라 localStorage 에만 저장한다(lib/storage.ts). */}
      <button
        type="button"
        className={`${styles.item} ${styles.themeButton}`}
        onClick={() => setTheme(nextTheme(theme))}
        title={`테마: ${THEME_LABEL[theme]} (누르면 전환)`}
        aria-label="테마 전환"
      >
        <span className={styles.icon}>
          <ThemeIcon />
        </span>
        {expanded && <span className={styles.label}>테마 · {THEME_LABEL[theme]}</span>}
      </button>
    </aside>
  );
}
