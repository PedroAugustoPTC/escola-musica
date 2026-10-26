import { useCallback, useEffect, useState } from 'react';
import { blockedRemainingSeconds } from '../../services/apiClient';

/**
 * Contagem regressiva do bloqueio contra sondagem de CPF (`OPERATION_TEMPORARILY_BLOCKED`).
 * `remainingSeconds` é `null` sem bloqueio (ou quando o tempo acabou). Chame `register(error)` no
 * `onError` da mutação: erros que não são de bloqueio são ignorados.
 */
export function useBlockedCountdown() {
  const [until, setUntil] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());

  const register = useCallback((error: unknown) => {
    const seconds = blockedRemainingSeconds(error);
    if (seconds === null) return;
    const current = Date.now();
    setNow(current);
    setUntil(current + seconds * 1000);
  }, []);

  useEffect(() => {
    if (until === null) return;
    const clock = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(clock);
  }, [until]);

  const remainingSeconds = until !== null && until > now ? Math.ceil((until - now) / 1000) : null;
  return { remainingSeconds, register };
}
