import { useEffect, useState } from 'react';
import styles from './ThemeToggle.module.css';

type ThemeChoice = 'system' | 'light' | 'dark';

/** 시스템 설정을 따르되 직접 고를 수 있다. 목업 원칙에 따라 선택은 저장하지 않는다(새로고침하면 시스템 설정). */
export default function ThemeToggle() {
  const [theme, setTheme] = useState<ThemeChoice>('system');

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'system') root.removeAttribute('data-theme');
    else root.setAttribute('data-theme', theme);
  }, [theme]);

  return (
    <label className={styles.toggle}>
      <span>테마</span>
      <select value={theme} onChange={(e) => setTheme(e.target.value as ThemeChoice)}>
        <option value="system">시스템</option>
        <option value="light">라이트</option>
        <option value="dark">다크</option>
      </select>
    </label>
  );
}
