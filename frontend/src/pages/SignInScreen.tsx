import { useEffect, useId, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { Button } from '../components/Button';
import { Icon } from '../components/Icon';
import { msg } from '../messages/messages';
import { ApiError } from '../services/apiClient';
import { useSignIn } from '../services/session';

type Status =
  | { type: 'normal' }
  | { type: 'invalid'; message: string }
  | { type: 'blocked'; until: number }
  | { type: 'no-connection' }
  | { type: 'error'; message: string };

function safeDestination(destination: string | null): string {
  return destination && destination.startsWith('/') && !destination.startsWith('//') ? destination : '/';
}

/** Tela 01 — Entrar (e variações 01b código inválido, 01c bloqueio, 01d sem conexão). */
export function SignInScreen() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const signin = useSignIn();
  const [code, setCode] = useState('');
  const [status, setStatus] = useState<Status>({ type: 'normal' });
  const [now, setNow] = useState(() => Date.now());
  const messageId = useId();
  const reason = params.get('reason');

  const blocked = status.type === 'blocked' && status.until > now;
  const remainingSeconds = status.type === 'blocked' ? Math.max(0, status.until - now) / 1000 : 0;

  useEffect(() => {
    if (status.type !== 'blocked') return;
    const clock = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(clock);
  }, [status]);

  const send = (event: FormEvent) => {
    event.preventDefault();
    if (blocked || signin.isPending) return;
    signin.mutate(code.trim(), {
      onSuccess: () => navigate(safeDestination(params.get('destination')), { replace: true }),
      onError: (error) => {
        if (error instanceof ApiError && error.status === 429) {
          const seconds = error.retryAfterSeconds ?? 15 * 60;
          setNow(Date.now());
          setStatus({ type: 'blocked', until: Date.now() + seconds * 1000 });
        } else if (error instanceof ApiError && error.status === 0) {
          setStatus({ type: 'no-connection' });
        } else if (error instanceof ApiError && error.status < 500) {
          setStatus({ type: 'invalid', message: error.message });
        } else {
          setStatus({ type: 'error', message: msg('INTERNAL_ERROR') });
        }
      },
    });
  };

  const invalid = status.type === 'invalid';

  return (
    <div className="app">
      <main id="content" className="app__main">
        <div className="sign-in">
          <div className="sign-in__brand">
            <img src="/brand/fb-logo-metal.png" alt="Instituto Musical Felipe Blaft" />
          </div>
          <div className="sign-in__form-side">
            <form className="sign-in__form" onSubmit={send} noValidate>
              <div>
                <div className="kicker">Portal do Professor</div>
                <h1 className="page-title sign-in__title">Entrar</h1>
                <p className="subtitle">
                  Informe o mesmo código de vinculação que você usa no Telegram para acessar seus alunos e
                  relatórios.
                </p>
              </div>
              {reason === 'SESSION_EXPIRED' && status.type === 'normal' ? (
                <div role="status" className="alert alert--info">
                  <Icon name="info" />
                  <div className="alert__text">{msg('SESSION_EXPIRED')}</div>
                </div>
              ) : null}
              {blocked ? (
                <div role="alert" className="alert alert--notice">
                  <Icon name="lock_clock" />
                  <div className="alert__text">
                    {msg('TEMPORARILY_BLOCKED', {
                      minutes: Math.max(1, Math.ceil(remainingSeconds / 60)),
                    })}
                  </div>
                </div>
              ) : null}
              {status.type === 'no-connection' ? (
                <div role="alert" className="alert alert--error">
                  <Icon name="wifi_off" />
                  <div className="alert__text">{msg('NO_CONNECTION')}</div>
                </div>
              ) : null}
              {status.type === 'error' ? (
                <div role="alert" className="alert alert--error">
                  <Icon name="error" />
                  <div className="alert__text">{status.message}</div>
                </div>
              ) : null}
              <div className="field">
                <label htmlFor="code" className="field__label">
                  Código de acesso
                </label>
                <input
                  id="code"
                  className="input input--code"
                  value={code}
                  onChange={(event) => {
                    setCode(event.target.value.toUpperCase());
                    if (invalid) setStatus({ type: 'normal' });
                  }}
                  disabled={blocked}
                  autoComplete="one-time-code"
                  autoCapitalize="characters"
                  spellCheck={false}
                  maxLength={16}
                  placeholder="K7M2-9QXT"
                  aria-invalid={invalid}
                  aria-describedby={messageId}
                />
                {invalid ? (
                  <div id={messageId} className="field__error" role="alert">
                    <Icon name="error" size="small" />
                    {status.message}
                  </div>
                ) : (
                  <div id={messageId} className="field__help">
                    O código tem 8 caracteres, letras e números.
                  </div>
                )}
              </div>
              <Button
                type="submit"
                size="large"
                disabled={blocked || code.trim().length === 0}
                loading={signin.isPending}
              >
                {signin.isPending ? 'Entrando…' : 'Entrar'}
              </Button>
              <div>
                <Link to="/admin/sign-in" className="button button--link">
                  {msg('ADMIN_LINK_FROM_SIGN_IN')}
                </Link>
              </div>
            </form>
          </div>
        </div>
      </main>
    </div>
  );
}
