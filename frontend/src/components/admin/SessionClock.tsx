import { useEffect, useRef, useState } from 'react';
import { msg } from '../../messages/messages';
import { Icon } from '../Icon';

/** Fuso da escola: o horário mostrado ao administrador não depende do fuso do navegador. */
const SCHOOL_TIME_ZONE = 'America/Sao_Paulo';
const WARNING_MS = 5 * 60_000;
const TICK_MS = 1000;

/** Minutos a mostrar no aviso prévio; `null` fora da janela de 5 minutos. */
function warningMinutes(remaining: number): number | null {
  return remaining > 0 && remaining <= WARNING_MS ? Math.max(1, Math.ceil(remaining / 60_000)) : null;
}

const timeFormat = new Intl.DateTimeFormat('pt-BR', {
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: SCHOOL_TIME_ZONE,
});

/** Horário "HH:MM" no fuso da escola. */
export function formatSchoolTime(instant: string): string {
  return timeFormat.format(new Date(instant));
}

/**
 * Relógio da sessão fixa de 2 h: mostra "Sua sessão expira às HH:MM", avisa uma única vez
 * (`role="alert"`) quando faltam 5 minutos e chama `onExpired` ao chegar a hora (WCAG 2.2.1: o limite
 * é essencial à segurança e o aviso prévio o mitiga).
 */
export function SessionClock({ expiresAt, onExpired }: { expiresAt: string; onExpired: () => void }) {
  const expiry = new Date(expiresAt).getTime();
  const [now, setNow] = useState(() => Date.now());
  // minutos mostrados no aviso, fixados na primeira exibição para o leitor de tela não reanunciar
  const [warnedMinutes, setWarnedMinutes] = useState<number | null>(() =>
    warningMinutes(expiry - Date.now()),
  );
  const expired = useRef(false);
  const onExpiredRef = useRef(onExpired);

  useEffect(() => {
    onExpiredRef.current = onExpired;
  }, [onExpired]);

  useEffect(() => {
    expired.current = false;
    const tick = () => {
      const current = Date.now();
      setNow(current);
      setWarnedMinutes((shown) => shown ?? warningMinutes(expiry - current));
    };
    const clock = window.setInterval(tick, TICK_MS);
    return () => window.clearInterval(clock);
  }, [expiry]);

  const remaining = expiry - now;

  useEffect(() => {
    if (remaining <= 0 && !expired.current) {
      expired.current = true;
      onExpiredRef.current();
    }
  }, [remaining]);

  return (
    <div className="session-clock">
      <p className="session-clock__time">
        <Icon name="schedule" size="small" />
        <span>{msg('ADMIN_SESSION_EXPIRES_AT', { time: formatSchoolTime(expiresAt) })}</span>
      </p>
      {warnedMinutes !== null ? (
        <div role="alert" className="alert alert--notice session-clock__warning">
          <Icon name="timer" />
          <div className="alert__text">{msg('ADMIN_SESSION_EXPIRES_SOON', { minutes: warnedMinutes })}</div>
        </div>
      ) : null}
    </div>
  );
}
