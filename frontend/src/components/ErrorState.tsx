import { Link } from 'react-router';
import { ApiError } from '../services/apiClient';
import { msg } from '../messages/messages';
import { Button } from './Button';
import { Icon } from './Icon';

/** Texto do catálogo para qualquer erro (nunca detalhe interno, FR-054). */
export function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return msg('INTERNAL_ERROR');
}

/**
 * Texto do catálogo pelo `code` do erro, ignorando a `message` do servidor (que pode ter detalhe que a
 * tela não deve mostrar). Código fora do catálogo vira `INTERNAL_ERROR`.
 */
export function catalogErrorText(error: unknown): string {
  if (error instanceof ApiError) return msg(error.code, { reference: error.reference });
  return msg('INTERNAL_ERROR');
}

/**
 * Alerta de erro dentro de uma tela, com "Tentar novamente" opcional. `fromCatalog` mostra sempre o texto
 * do catálogo pelo código do erro, nunca a `message` crua do servidor.
 */
export function ErrorAlert({
  error,
  onRetry,
  fromCatalog = false,
  consentStudentId,
}: {
  error: unknown;
  onRetry?: () => void;
  fromCatalog?: boolean;
  /** Aluno do aviso `CONSENT_REQUIRED`: mostra o atalho para registrar o consentimento. */
  consentStudentId?: string;
}) {
  const offline = error instanceof ApiError && error.status === 0;
  const consentBlock = error instanceof ApiError && error.code === 'CONSENT_REQUIRED' && consentStudentId;
  return (
    <div role="alert" className="alert alert--error">
      <Icon name={offline ? 'wifi_off' : 'error'} />
      <div className="alert__text">{fromCatalog ? catalogErrorText(error) : errorText(error)}</div>
      {onRetry ? (
        <Button variant="danger" size="compact" onClick={onRetry}>
          Tentar novamente
        </Button>
      ) : null}
      {consentBlock ? (
        <Link className="button button--link" to={`/my-students/${consentStudentId}`}>
          {msg('CONSENT_RECORD_BUTTON')}
        </Link>
      ) : null}
    </div>
  );
}

/** Estado de erro de página inteira. */
export function ErrorState({
  error,
  onRetry,
  fromCatalog = false,
}: {
  error: unknown;
  onRetry?: () => void;
  fromCatalog?: boolean;
}) {
  return (
    <div className="page page--narrow">
      <ErrorAlert error={error} onRetry={onRetry} fromCatalog={fromCatalog} />
    </div>
  );
}
