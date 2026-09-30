import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from 'react';

export type ThemeChoice = 'system' | 'light' | 'dark';

interface ThemeStore {
  theme: ThemeChoice;
  setTheme: (theme: ThemeChoice) => void;
}

const ThemeContext = createContext<ThemeStore | null>(null);

/** 테마 선택을 앱 전체에 적용한다. 목업 원칙에 따라 저장하지 않는다(새로고침하면 시스템 설정). */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<ThemeChoice>('system');

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'system') root.removeAttribute('data-theme');
    else root.setAttribute('data-theme', theme);
  }, [theme]);

  const store = useMemo(() => ({ theme, setTheme }), [theme]);

  return <ThemeContext.Provider value={store}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeStore {
  const store = useContext(ThemeContext);
  if (!store) throw new Error('useTheme must be used inside ThemeProvider');
  return store;
}
