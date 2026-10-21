import { useId, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { Button } from '../../components/Button';
import { Icon } from '../../components/Icon';
import { BlockedNotice, useBlockedCountdown } from '../../components/admin/useBlockedCountdown';
import { apiErrorText } from '../../components/admin/fieldErrors';
import { msg } from '../../messages/messages';
import { ApiError, blockedRemainingSeconds } from '../../services/apiClient';
import { isAdminDestination, useAdminSignIn } from '../../services/admin/session';

type Failure = 'none' | 'credentials' | 'no-connection' | 'error';

/** A01 — Entrar na área administrativa (e-mail e senha; mensagem única para qualquer falha). */
export function AdminSignInScreen() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const signIn = useAdminSignIn();
  const countdown = useBlockedCountdown();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [missing, setMissing] = useState({ email: false, password: false });
  const [failure, setFailure] = useState<Failure>('none');
  const [errorMessage, setErrorMessage] = useState('');
  const emailErrorId = useId();
  const passwordErrorId = useId();
  const reason = params.get('reason');

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (countdown.blocked || signIn.isPending) return;
    const pending = { email: email.trim() === '', password: password === '' };
    setMissing(pending);
    if (pending.email || pending.password) return;
    setFailure('none');
    signIn.mutate(
      { email: email.trim(), password },
      {
        onSuccess: (session) => {
          setPassword('');
          const destination = params.get('destination');
          navigate(
            session.mustChangePassword
              ? '/admin/change-password'
              : isAdminDestination(destination)
                ? destination
                : '/admin',
            { replace: true },
          );
        },
        onError: (error) => {
          setPassword('');
          const blockedFor = blockedRemainingSeconds(error);
          if (blockedFor !== null) {
            countdown.block(blockedFor);
          } else if (error instanceof ApiError && error.status === 0) {
            setFailure('no-connection');
          } else if (error instanceof ApiError && error.status < 500) {
            // mesma mensagem para e-mail inexistente, senha errada, conta inativa ou não ativada
            setFailure('credentials');
          } else {
            setErrorMessage(apiErrorText(error));
            setFailure('error');
          }
        },
      },
    );
  };

  return (
    <div className="app">
      <main id="content" className="app__main">
        <div className="sign-in">
          <div className="sign-in__brand">
            <img src="/brand/fb-logo-metal.png" alt="Instituto Musical Felipe Blaft" />
          </div>
          <div className="sign-in__form-side">
            <form className="sign-in__form" onSubmit={submit} noValidate>
              <div>
                <div className="kicker">{msg('ADMIN_AREA_TITLE')}</div>
                <h1 className="page-title sign-in__title">{msg('ADMIN_SIGN_IN_TITLE')}</h1>
              </div>
              {reason === 'ACCESS_ENDED' ? (
                <div className="alert alert--info">
                  <Icon name="info" />
                  <div className="alert__text">{msg('ACCESS_ENDED')}</div>
                </div>
              ) : null}
              {reason === 'SESSION_EXPIRED' ? (
                <div className="alert alert--info">
                  <Icon name="info" />
                  <div className="alert__text">Sua sessão terminou. Entre de novo para continuar.</div>
                </div>
              ) : null}
              <BlockedNotice blocked={countdown.blocked} remainingSeconds={countdown.remainingSeconds} />
              {failure === 'credentials' ? (
                <div role="alert" className="alert alert--error">
                  <Icon name="error" />
                  <div className="alert__text">{msg('INVALID_CREDENTIALS')}</div>
                </div>
              ) : null}
              {failure === 'no-connection' ? (
                <div role="alert" className="alert alert--error">
                  <Icon name="wifi_off" />
                  <div className="alert__text">{msg('NO_CONNECTION')}</div>
                </div>
              ) : null}
              {failure === 'error' ? (
                <div role="alert" className="alert alert--error">
                  <Icon name="error" />
                  <div className="alert__text">{errorMessage}</div>
                </div>
              ) : null}
              <div className="field">
                <label htmlFor="admin-email" className="field__label">
                  E-mail
                </label>
                <input
                  id="admin-email"
                  type="email"
                  className="input"
                  value={email}
                  onChange={(event) => {
                    setEmail(event.target.value);
                    setMissing((current) => ({ ...current, email: false }));
                  }}
                  autoComplete="username"
                  autoCapitalize="none"
                  spellCheck={false}
                  maxLength={254}
                  aria-invalid={missing.email}
                  aria-describedby={missing.email ? emailErrorId : undefined}
                />
                {missing.email ? (
                  <div id={emailErrorId} className="field__error">
                    <Icon name="error" size="small" />
                    Informe o e-mail.
                  </div>
                ) : null}
              </div>
              <div className="field">
                <label htmlFor="admin-password" className="field__label">
                  Senha
                </label>
                <input
                  id="admin-password"
                  type="password"
                  className="input"
                  value={password}
                  onChange={(event) => {
                    setPassword(event.target.value);
                    setMissing((current) => ({ ...current, password: false }));
                  }}
                  autoComplete="current-password"
                  maxLength={128}
                  aria-invalid={missing.password}
                  aria-describedby={missing.password ? passwordErrorId : undefined}
                />
                {missing.password ? (
                  <div id={passwordErrorId} className="field__error">
                    <Icon name="error" size="small" />
                    Informe a senha.
                  </div>
                ) : null}
              </div>
              <Button type="submit" size="large" disabled={countdown.blocked} loading={signIn.isPending}>
                {signIn.isPending ? 'Entrando…' : 'Entrar'}
              </Button>
              <p className="support-text">
                <Link to="/admin/activate">{msg('ADMIN_HAVE_ACTIVATION_CODE')}</Link>
              </p>
              <p className="support-text">
                <Link to="/sign-in">Sou professor</Link>
              </p>
            </form>
          </div>
        </div>
      </main>
    </div>
  );
}
