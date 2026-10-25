import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { Button } from '../../components/Button';
import { Icon } from '../../components/Icon';
import { BlockedNotice, useBlockedCountdown } from '../../components/admin/useBlockedCountdown';
import {
  apiErrorText,
  errorOfField,
  fieldErrorText,
  fieldErrorsOf,
} from '../../components/admin/fieldErrors';
import {
  checkNewPassword,
  NewPasswordFields,
  type NewPasswordErrors,
} from '../../components/admin/NewPasswordFields';
import { FormErrorSummary, TextField } from '../../components/admin/ui';
import { msg } from '../../messages/messages';
import { blockedRemainingSeconds } from '../../services/apiClient';
import { useActivateAdministrator } from '../../services/admin/session';

interface Missing extends NewPasswordErrors {
  email?: string;
  code?: string;
}

/**
 * A02 — Definir a senha com o código de ativação (sem credencial). Não abre sessão: depois de definir,
 * o administrador entra em A01. Tentativas erradas usam o bloqueio por origem do servidor.
 */
export function ActivateScreen() {
  const activate = useActivateAdministrator();
  const countdown = useBlockedCountdown();
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [missing, setMissing] = useState<Missing>({});
  const [done, setDone] = useState(false);

  const fields = fieldErrorsOf(activate.error);
  const passwordServer = errorOfField(fields, 'newPassword');
  const emailServer = errorOfField(fields, 'email');
  const blockedFor = blockedRemainingSeconds(activate.error);

  const passwordErrors: NewPasswordErrors = {
    newPassword: missing.newPassword ?? (passwordServer ? fieldErrorText(passwordServer.code) : undefined),
    confirmation: missing.confirmation,
  };
  const emailError = missing.email ?? (emailServer ? fieldErrorText(emailServer.code) : undefined);
  const summary = [emailError, missing.code, passwordErrors.newPassword, passwordErrors.confirmation].filter(
    (text): text is string => Boolean(text),
  );
  if (summary.length === 0 && activate.isError && blockedFor === null && fields.length === 0) {
    summary.push(
      activate.error.code === 'INVALID_ACTIVATION' ? msg('INVALID_ACTIVATION') : apiErrorText(activate.error),
    );
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (countdown.blocked || activate.isPending) return;
    const password = checkNewPassword(newPassword, confirmation);
    const pending: Missing = {
      ...password,
      email: email.trim() === '' ? 'Informe o e-mail.' : undefined,
      code: code.trim() === '' ? 'Informe o código de ativação.' : undefined,
    };
    setMissing(pending);
    if (pending.email || pending.code || pending.newPassword || pending.confirmation) return;
    activate.mutate(
      { email: email.trim(), activationCode: code.trim(), newPassword },
      {
        onSuccess: () => {
          setNewPassword('');
          setConfirmation('');
          setDone(true);
        },
        onError: (error) => {
          setNewPassword('');
          setConfirmation('');
          const wait = blockedRemainingSeconds(error);
          if (wait !== null) countdown.block(wait);
        },
      },
    );
  };

  if (done) {
    return (
      <div className="app">
        <main id="content" className="app__main">
          <div className="page page--center">
            <div role="status" className="alert alert--ok">
              <Icon name="check_circle" />
              <div className="alert__text">Senha definida. Agora você já pode entrar.</div>
            </div>
            <Link to="/admin/sign-in" className="button button--primary button--large">
              Entrar
            </Link>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="app">
      <main id="content" className="app__main">
        <div className="page page--center">
          <div>
            <div className="kicker">{msg('ADMIN_AREA_TITLE')}</div>
            <h1 className="page-title">Definir a senha</h1>
            <p className="subtitle">
              Informe o e-mail, o código de ativação que você recebeu e a senha nova.
            </p>
          </div>
          <form className="admin-form" onSubmit={submit} noValidate>
            <BlockedNotice blocked={countdown.blocked} remainingSeconds={countdown.remainingSeconds} />
            <FormErrorSummary messages={summary} />
            <TextField
              label="E-mail"
              type="email"
              value={email}
              onChange={(event) => {
                setEmail(event.target.value);
                setMissing((current) => ({ ...current, email: undefined }));
              }}
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              maxLength={254}
              error={emailError}
            />
            <TextField
              label="Código de ativação"
              type="text"
              className="input--code"
              value={code}
              onChange={(event) => {
                setCode(event.target.value.toUpperCase());
                setMissing((current) => ({ ...current, code: undefined }));
              }}
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
              maxLength={20}
              error={missing.code}
            />
            <NewPasswordFields
              newPassword={newPassword}
              confirmation={confirmation}
              onNewPassword={(value) => {
                setNewPassword(value);
                setMissing((current) => ({ ...current, newPassword: undefined }));
              }}
              onConfirmation={(value) => {
                setConfirmation(value);
                setMissing((current) => ({ ...current, confirmation: undefined }));
              }}
              errors={passwordErrors}
            />
            <div className="admin-form__actions">
              <Button type="submit" size="large" disabled={countdown.blocked} loading={activate.isPending}>
                Definir senha
              </Button>
              <Link to="/admin/sign-in" className="button button--link">
                Já tenho senha
              </Link>
            </div>
          </form>
        </div>
      </main>
    </div>
  );
}
