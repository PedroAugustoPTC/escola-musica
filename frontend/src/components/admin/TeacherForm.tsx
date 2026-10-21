import { useState, type FormEvent, type ReactNode } from 'react';
import { ApiError } from '../../services/apiClient';
import { Button } from '../Button';
import { apiErrorText, errorOfField, fieldErrorsOf, fieldErrorText } from './fieldErrors';
import { FormErrorSummary, TextField } from './ui';

export interface TeacherFormValues {
  name: string;
  email: string;
}

/**
 * Formulário de professor (A06 cadastro, A08 edição): nome e e-mail. A validação definitiva é do servidor;
 * aqui só se confere campo vazio. Erros por campo vêm de `details.fields`; e-mail repetido mostra o nome
 * do professor existente.
 */
export function TeacherForm({
  initial,
  submitLabel,
  submitting,
  error,
  onSubmit,
  secondaryAction,
}: {
  initial?: TeacherFormValues;
  submitLabel: string;
  submitting: boolean;
  error: unknown;
  onSubmit: (values: TeacherFormValues) => void;
  secondaryAction?: ReactNode;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [email, setEmail] = useState(initial?.email ?? '');
  const [missing, setMissing] = useState<{ name?: string; email?: string }>({});

  const serverFields = fieldErrorsOf(error);
  const duplicateEmail = error instanceof ApiError && error.code === 'DUPLICATE_EMAIL';
  const nameServer = errorOfField(serverFields, 'name');
  const emailServer = errorOfField(serverFields, 'email');
  const nameError = missing.name ?? (nameServer ? fieldErrorText(nameServer.code) : undefined);
  const emailError =
    missing.email ??
    (duplicateEmail ? apiErrorText(error) : emailServer ? fieldErrorText(emailServer.code) : undefined);

  const summary: string[] = [];
  if (missing.name) summary.push(missing.name);
  if (missing.email) summary.push(missing.email);
  if (summary.length === 0) {
    if (serverFields.length > 0) {
      for (const item of serverFields) summary.push(fieldErrorText(item.code));
    } else if (error) {
      summary.push(apiErrorText(error));
    }
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (submitting) return;
    const pending = {
      name: name.trim() === '' ? 'Informe o nome.' : undefined,
      email: email.trim() === '' ? 'Informe o e-mail.' : undefined,
    };
    setMissing(pending);
    if (pending.name || pending.email) return;
    onSubmit({ name: name.trim(), email: email.trim() });
  };

  return (
    <form className="admin-form" onSubmit={submit} noValidate>
      <FormErrorSummary messages={summary} />
      <TextField
        label="Nome"
        value={name}
        onChange={(event) => {
          setName(event.target.value);
          setMissing((current) => ({ ...current, name: undefined }));
        }}
        error={nameError}
        autoComplete="off"
        maxLength={120}
      />
      <TextField
        label="E-mail"
        type="email"
        value={email}
        onChange={(event) => {
          setEmail(event.target.value);
          setMissing((current) => ({ ...current, email: undefined }));
        }}
        error={emailError}
        autoComplete="off"
        autoCapitalize="none"
        spellCheck={false}
        maxLength={254}
      />
      <div className="admin-form__actions">
        <Button type="submit" size="large" loading={submitting}>
          {submitLabel}
        </Button>
        {secondaryAction}
      </div>
    </form>
  );
}
