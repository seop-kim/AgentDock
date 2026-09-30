import { ComponentType, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { DashboardIcon, MenuIcon, PaletteIcon, PlugIcon, SettingsIcon } from './icons';
import styles from './Sidebar.module.css';

interface MenuItem {
  to: string;
  label: string;
  Icon: ComponentType;
}

interface MenuGroup extends MenuItem {
  children?: MenuItem[];
}

const MENUS: MenuGroup[] = [
  { to: '/', label: 'Dashboard', Icon: DashboardIcon },
  {
    to: '/settings',
    label: '설정',
    Icon: SettingsIcon,
    children: [
      { to: '/settings/agents', label: '에이전트 연결 설정', Icon: PlugIcon },
      { to: '/settings/theme', label: '테마', Icon: PaletteIcon },
    ],
  },
];

/**
 * 왼쪽 메뉴. 기본은 아이콘만 보이고, 위의 메뉴 버튼을 누르면 이름까지 펼쳐지며 다시 누르면 접힌다.
 * 하위 메뉴는 펼쳤을 때, 또는 접힌 상태여도 그 그룹의 화면에 있을 때 그 아래에 아이콘으로 보인다.
 */
export default function Sidebar() {
  const [expanded, setExpanded] = useState(false);
  const { pathname } = useLocation();

  const renderItem = ({ to, label, Icon }: MenuItem, className: string) => (
    <NavLink
      key={to}
      to={to}
      end={to === '/'}
      className={({ isActive }) => `${styles.item} ${className} ${isActive ? styles.active : ''}`}
      title={expanded ? undefined : label}
      aria-label={label}
    >
      <span className={styles.icon}>
        <Icon />
      </span>
      {expanded && <span className={styles.label}>{label}</span>}
    </NavLink>
  );

  return (
    <aside className={`${styles.sidebar} ${expanded ? styles.expanded : ''}`}>
      <button
        type="button"
        className={styles.item}
        onClick={() => setExpanded((prev) => !prev)}
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
        {MENUS.map((menu) => {
          const inGroup = menu.children !== undefined && pathname.startsWith(menu.to);
          const showChildren = menu.children !== undefined && (expanded || inGroup);
          return (
            <div key={menu.to} className={styles.group}>
              {renderItem(menu, '')}
              {showChildren && menu.children!.map((child) => renderItem(child, expanded ? styles.child : styles.childCollapsed))}
            </div>
          );
        })}
      </nav>
    </aside>
  );
}
