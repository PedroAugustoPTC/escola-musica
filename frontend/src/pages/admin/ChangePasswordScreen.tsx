import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router';
import { Button } from '../../components/Button';
import { LoadingState } from '../../components/LoadingState';
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
import { FormErrorSummary } from '../../components/admin/ui';
import { msg } from '../../messages/messages';
import type { ApiError } from '../../services/apiClient';
import { useAdminSession, useChangeProvisionalPassword } from '../../services/admin/session';

/**
 * A03 — Trocar a senha provisória. Única tela administrativa permitida enquanto `mustChangePassword`
 * for verdadeiro (o `AdminSessionGuard` redireciona as demais para cá).
 */
export function ChangePasswordScreen() {
  const navigate = useNavigate();
  const session = useAdminSession();
  const change = useChangeProvisionalPassword();
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [local, setLocal] = useState<NewPasswordErrors>({});
  // a falha é copiada para o estado da tela e a mutação é zerada: a senha não fica em memória
  const [failure, setFailure] = useState<ApiError | null>(null);

  if (session.isPending) return <LoadingState label="Carregando" rows={2} />;
  // quem já definiu a senha não tem o que trocar aqui
  if (session.data && !session.data.mustChangePassword && !change.isPending) {
    return <Navigate to="/admin" replace />;
  }

  const serverField = errorOfField(fieldErrorsOf(failure), 'newPassword');
  const errors: NewPasswordErrors = {
    newPassword: local.newPassword ?? (serverField ? fieldErrorText(serverField.code) : undefined),
    confirmation: local.confirmation,
  };
  const summary = [errors.newPassword, errors.confirmation].filter((text): text is string => Boolean(text));
  if (summary.length === 0 && failure !== null && !serverField) summary.push(apiErrorText(failure));

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (change.isPending) return;
    const found = checkNewPassword(newPassword, confirmation);
    setLocal(found);
    if (found.newPassword || found.confirmation) return;
    setFailure(null);
    change.mutate(newPassword, {
      onSuccess: () => {
        setNewPassword('');
        setConfirmation('');
        change.reset();
        navigate('/admin', { replace: true });
      },
      onError: (error) => {
        setNewPassword('');
        setConfirmation('');
        setFailure(error);
        change.reset();
      },
    });
  };

  return (
    <div className="page page--narrow">
      <div>
        <div className="kicker">{msg('ADMIN_AREA_TITLE')}</div>
        <h1 className="page-title">{msg('ADMIN_FORCE_PASSWORD_TITLE')}</h1>
        <p className="subtitle">{msg('PASSWORD_CHANGE_REQUIRED')}</p>
      </div>
      <form className="admin-form" onSubmit={submit} noValidate>
        <FormErrorSummary messages={summary} />
        <NewPasswordFields
          newPassword={newPassword}
          confirmation={confirmation}
          onNewPassword={(value) => {
            setNewPassword(value);
            setLocal((current) => ({ ...current, newPassword: undefined }));
          }}
          onConfirmation={(value) => {
            setConfirmation(value);
            setLocal((current) => ({ ...current, confirmation: undefined }));
          }}
          errors={errors}
        />
        <div className="admin-form__actions">
          <Button type="submit" size="large" loading={change.isPending}>
            Trocar senha
          </Button>
        </div>
      </form>
    </div>
  );
}
