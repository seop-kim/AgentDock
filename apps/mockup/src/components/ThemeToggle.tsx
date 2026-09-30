import { useEffect, useState } from 'react';
import { MonitorIcon, MoonIcon, SunIcon } from './icons';
import styles from './Sidebar.module.css';

type ThemeChoice = 'system' | 'light' | 'dark';

const NEXT: Record<ThemeChoice, ThemeChoice> = { system: 'light', light: 'dark', dark: 'system' };
const LABEL: Record<ThemeChoice, string> = { system: '테마: 시스템', light: '테마: 라이트', dark: '테마: 다크' };
const ICON: Record<ThemeChoice, () => JSX.Element> = { system: MonitorIcon, light: SunIcon, dark: MoonIcon };

/** 누를 때마다 시스템 → 라이트 → 다크 순으로 바꾼다. 목업 원칙에 따라 선택은 저장하지 않는다. */
export default function ThemeToggle({ expanded }: { expanded: boolean }) {
  const [theme, setTheme] = useState<ThemeChoice>('system');

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'system') root.removeAttribute('data-theme');
    else root.setAttribute('data-theme', theme);
  }, [theme]);

  const ThemeIcon = ICON[theme];

  return (
    <button
      type="button"
      className={styles.item}
      onClick={() => setTheme(NEXT[theme])}
      title={expanded ? undefined : LABEL[theme]}
      aria-label={LABEL[theme]}
    >
      <span className={styles.icon}>
        <ThemeIcon />
      </span>
      {expanded && <span className={styles.label}>{LABEL[theme]}</span>}
    </button>
  );
}
