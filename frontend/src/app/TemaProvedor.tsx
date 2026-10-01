import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export type Tema = 'claro' | 'escuro';

const CHAVE = 'relatorios.tema';

interface ContextoTema {
  tema: Tema;
  alternar: () => void;
  /** Variação dos ativos da marca sobre o fundo do tema (tinta do texto). */
  tinta: 'preto' | 'marfim';
  /** Variação dourada dos ativos da marca. */
  tintaOuro: 'ouroclaro' | 'ouro';
}

const Contexto = createContext<ContextoTema | null>(null);

function lerPreferencia(): Tema {
  try {
    const salvo = window.localStorage.getItem(CHAVE);
    if (salvo === 'claro' || salvo === 'escuro') return salvo;
  } catch {
    // armazenamento indisponível: segue a preferência do sistema
  }
  try {
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'escuro' : 'claro';
  } catch {
    return 'claro';
  }
}

/** Tema claro/escuro (FR-052): escolhido no menu e lembrado; na 1ª visita segue o sistema. */
export function TemaProvedor({ children }: { children: ReactNode }) {
  const [tema, setTema] = useState<Tema>(lerPreferencia);

  useEffect(() => {
    document.documentElement.dataset.tema = tema;
    const cor = tema === 'escuro' ? '#0B0B0B' : '#FAF7F0';
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', cor);
  }, [tema]);

  const alternar = useCallback(() => {
    setTema((atual) => {
      const novo: Tema = atual === 'escuro' ? 'claro' : 'escuro';
      try {
        window.localStorage.setItem(CHAVE, novo);
      } catch {
        // sem armazenamento: vale só para esta visita
      }
      return novo;
    });
  }, []);

  const valor = useMemo<ContextoTema>(
    () => ({
      tema,
      alternar,
      tinta: tema === 'escuro' ? 'marfim' : 'preto',
      tintaOuro: tema === 'escuro' ? 'ouro' : 'ouroclaro',
    }),
    [tema, alternar],
  );

  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useTema(): ContextoTema {
  const contexto = useContext(Contexto);
  if (!contexto) throw new Error('useTema fora do TemaProvedor');
  return contexto;
}
