import { ApiError } from '../../services/apiClient';
import { hasMessage, msg } from '../../messages/messages';

export interface FieldError {
  field: string;
  code: string;
}

/** Violações por campo de uma resposta 400 `VALIDATION_FAILED` (`details.fields`); vazio nos demais erros. */
export function fieldErrorsOf(error: unknown): FieldError[] {
  if (!(error instanceof ApiError)) return [];
  const fields = error.details.fields;
  if (!Array.isArray(fields)) return [];
  return fields.filter(
    (item): item is FieldError =>
      typeof item === 'object' &&
      item !== null &&
      typeof (item as FieldError).field === 'string' &&
      typeof (item as FieldError).code === 'string',
  );
}

/** Texto do catálogo para o código de campo; sem texto próprio, a mensagem geral de validação. */
export function fieldErrorText(code: string): string {
  return hasMessage(code) ? msg(code) : msg('VALIDATION_FAILED');
}

/** Primeiro erro do campo (ou `undefined`). */
export function errorOfField(errors: FieldError[], field: string): FieldError | undefined {
  return errors.find((item) => item.field === field);
}

/** Nome do registro existente em `DUPLICATE_*` (só para o administrador). */
export function duplicateName(error: unknown): string {
  if (!(error instanceof ApiError)) return '';
  const name = error.details.name;
  return typeof name === 'string' ? name : '';
}

/** Texto do erro com os placeholders do catálogo preenchidos (ex.: `{name}` de `DUPLICATE_EMAIL`). */
export function apiErrorText(error: unknown): string {
  if (!(error instanceof ApiError)) return msg('INTERNAL_ERROR');
  if (error.code === 'DUPLICATE_EMAIL' || error.code === 'DUPLICATE_CPF') {
    return msg(error.code, { name: duplicateName(error) });
  }
  return hasMessage(error.code) ? msg(error.code, { reference: error.reference }) : error.message;
}
