import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { readStorage, STORAGE_KEYS, writeStorage } from '../lib/storage';

export type ThemeChoice = 'system' | 'light' | 'dark';

interface ThemeStore {
  theme: ThemeChoice;
  setTheme: (theme: ThemeChoice) => void;
}

const ThemeContext = createContext<ThemeStore | null>(null);

const isThemeChoice = (value: string | null): value is ThemeChoice =>
  value === 'system' || value === 'light' || value === 'dark';

function readStoredTheme(): ThemeChoice {
  const stored = readStorage(STORAGE_KEYS.theme);
  return isThemeChoice(stored) ? stored : 'system';
}

/** 테마 선택을 앱 전체에 적용하고 localStorage 에 저장한다. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeChoice>(readStoredTheme);

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'system') root.removeAttribute('data-theme');
    else root.setAttribute('data-theme', theme);
  }, [theme]);

  const store = useMemo<ThemeStore>(
    () => ({
      theme,
      setTheme: (next) => {
        setThemeState(next);
        writeStorage(STORAGE_KEYS.theme, next);
      },
    }),
    [theme],
  );

  return <ThemeContext.Provider value={store}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeStore {
  const store = useContext(ThemeContext);
  if (!store) throw new Error('useTheme must be used inside ThemeProvider');
  return store;
}
