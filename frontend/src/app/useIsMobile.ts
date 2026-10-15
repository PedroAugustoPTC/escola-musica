import { useSyncExternalStore } from 'react';

const QUERY = '(max-width: 899.98px)';

function sign(onChange: () => void): () => void {
  if (typeof window.matchMedia !== 'function') return () => undefined;
  const list = window.matchMedia(QUERY);
  list.addEventListener('change', onChange);
  return () => list.removeEventListener('change', onChange);
}

function currentValue(): boolean {
  if (typeof window.matchMedia !== 'function') return window.innerWidth < 900;
  return window.matchMedia(QUERY).matches;
}

/** Layout móvel abaixo de 900 px, como nos protótipos (390 px × 1440 px). */
export function useIsMobile(): boolean {
  return useSyncExternalStore(sign, currentValue, () => false);
}
