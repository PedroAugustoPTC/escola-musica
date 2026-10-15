import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export type Theme = 'light' | 'dark';

const KEY = 'reports.theme';

/** Valores guardados no navegador (nomes do armazenamento); o tema interno segue 'claro'/'escuro' até a fatia 5. */
const STORED: Record<Theme, string> = { light: 'light', dark: 'dark' };

interface ThemeContextValue {
  theme: Theme;
  toggle: () => void;
  /** Variação dos ativos da marca sobre o fundo do tema (tinta do texto). */
  ink: 'black' | 'ivory';
  /** Variação dourada dos ativos da marca. */
  goldInk: 'lightgold' | 'gold';
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function readPreference(): Theme {
  try {
    const saved = window.localStorage.getItem(KEY);
    if (saved === STORED.light) return 'light';
    if (saved === STORED.dark) return 'dark';
  } catch {
    // armazenamento indisponível: segue a preferência do sistema
  }
  try {
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

/** Tema claro/escuro (FR-052): escolhido no menu e lembrado; na 1ª visita segue o sistema. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(readPreference);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    const color = theme === 'dark' ? '#0B0B0B' : '#FAF7F0';
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', color);
  }, [theme]);

  const toggle = useCallback(() => {
    setTheme((current) => {
      const nextValue: Theme = current === 'dark' ? 'light' : 'dark';
      try {
        window.localStorage.setItem(KEY, STORED[nextValue]);
      } catch {
        // sem armazenamento: vale só para esta visita
      }
      return nextValue;
    });
  }, []);

  const value = useMemo<ThemeContextValue>(
    () => ({
      theme,
      toggle,
      ink: theme === 'dark' ? 'ivory' : 'black',
      goldInk: theme === 'dark' ? 'gold' : 'lightgold',
    }),
    [theme, toggle],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme fora do ThemeProvider');
  return context;
}
