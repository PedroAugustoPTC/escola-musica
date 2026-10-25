import { msg } from '../../messages/messages';
import { TextField } from './ui';

export interface NewPasswordErrors {
  newPassword?: string;
  confirmation?: string;
}

/** Conferência só de forma (campo vazio e confirmação igual); o tamanho e as demais regras são do servidor. */
export function checkNewPassword(newPassword: string, confirmation: string): NewPasswordErrors {
  const errors: NewPasswordErrors = {};
  if (newPassword === '') errors.newPassword = 'Informe a nova senha.';
  if (confirmation === '') errors.confirmation = 'Confirme a nova senha.';
  else if (newPassword !== '' && confirmation !== newPassword)
    errors.confirmation = 'As senhas não são iguais.';
  return errors;
}

/**
 * Nova senha e confirmação (`autocomplete="new-password"`). A regra "12 a 128 caracteres" fica visível
 * antes do envio; não há medidor de força.
 */
export function NewPasswordFields({
  newPassword,
  confirmation,
  onNewPassword,
  onConfirmation,
  errors,
}: {
  newPassword: string;
  confirmation: string;
  onNewPassword: (value: string) => void;
  onConfirmation: (value: string) => void;
  errors: NewPasswordErrors;
}) {
  return (
    <>
      <TextField
        label="Nova senha"
        type="password"
        value={newPassword}
        onChange={(event) => onNewPassword(event.target.value)}
        autoComplete="new-password"
        maxLength={128}
        help={msg('PASSWORD_LENGTH')}
        error={errors.newPassword}
      />
      <TextField
        label="Confirme a nova senha"
        type="password"
        value={confirmation}
        onChange={(event) => onConfirmation(event.target.value)}
        autoComplete="new-password"
        maxLength={128}
        error={errors.confirmation}
      />
    </>
  );
}
