import { useSyncExternalStore } from 'react';

const QUERY = '(max-width: 639.98px)';

function subscribe(onChange: () => void): () => void {
  if (typeof window.matchMedia !== 'function') return () => undefined;
  const list = window.matchMedia(QUERY);
  list.addEventListener('change', onChange);
  return () => list.removeEventListener('change', onChange);
}

function current(): boolean {
  if (typeof window.matchMedia !== 'function') return window.innerWidth < 640;
  return window.matchMedia(QUERY).matches;
}

/** Verdadeiro abaixo de 640 px: cartões no lugar da tabela e filtros recolhíveis. */
export function useCompactLayout(): boolean {
  return useSyncExternalStore(subscribe, current, () => false);
}
