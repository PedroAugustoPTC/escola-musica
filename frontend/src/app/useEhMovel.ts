import { useSyncExternalStore } from 'react';

const CONSULTA = '(max-width: 899.98px)';

function assinar(aoMudar: () => void): () => void {
  if (typeof window.matchMedia !== 'function') return () => undefined;
  const lista = window.matchMedia(CONSULTA);
  lista.addEventListener('change', aoMudar);
  return () => lista.removeEventListener('change', aoMudar);
}

function valorAtual(): boolean {
  if (typeof window.matchMedia !== 'function') return window.innerWidth < 900;
  return window.matchMedia(CONSULTA).matches;
}

/** Layout móvel abaixo de 900 px, como nos protótipos (390 px × 1440 px). */
export function useEhMovel(): boolean {
  return useSyncExternalStore(assinar, valorAtual, () => false);
}
