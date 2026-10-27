import { useCallback, useEffect, useState } from 'react';
import { msg } from '../../messages/messages';
import { Icon } from '../Icon';

const DEFAULT_BLOCK_SECONDS = 15 * 60;

/** Contagem regressiva de um bloqueio temporário (tempo restante vindo do servidor). */
export function useBlockedCountdown() {
  const [until, setUntil] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (until === null) return;
    const clock = window.setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (current >= until) setUntil(null);
    }, 1000);
    return () => window.clearInterval(clock);
  }, [until]);

  const block = useCallback((seconds: number | null | undefined) => {
    const wait = seconds && seconds > 0 ? seconds : DEFAULT_BLOCK_SECONDS;
    setNow(Date.now());
    setUntil(Date.now() + wait * 1000);
  }, []);

  const remainingSeconds = until === null ? 0 : Math.max(0, Math.ceil((until - now) / 1000));
  return { blocked: until !== null && remainingSeconds > 0, remainingSeconds, block };
}

/** "mm:ss" para a contagem regressiva visível. */
export function formatCountdown(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

/**
 * Aviso de bloqueio: o texto do catálogo é formatado a partir do tempo restante (nunca o texto cru) e
 * fica numa região `aria-live="polite"` que muda só quando o número de minutos muda; a contagem
 * "mm:ss" fica fora da região para o leitor de tela não anunciar a cada segundo.
 */
export function BlockedNotice({
  blocked,
  remainingSeconds,
  code = 'TEMPORARILY_BLOCKED',
}: {
  blocked: boolean;
  remainingSeconds: number;
  code?: 'TEMPORARILY_BLOCKED' | 'OPERATION_TEMPORARILY_BLOCKED';
}) {
  return (
    <>
      <div role="status" aria-live="polite">
        {blocked ? (
          <div className="alert alert--notice">
            <Icon name="lock_clock" />
            <div className="alert__text">
              {msg(code, { minutes: Math.max(1, Math.ceil(remainingSeconds / 60)) })}
            </div>
          </div>
        ) : null}
      </div>
      {blocked ? (
        <p className="support-text blocked-countdown">
          <Icon name="timer" size="small" /> <span>Tempo restante: {formatCountdown(remainingSeconds)}</span>
        </p>
      ) : null}
    </>
  );
}
