import { ApiError } from '../services/apiClient';
import { msg } from '../messages/messages';
import { Button } from './Button';
import { Icon } from './Icon';

/** Texto do catálogo para qualquer erro (nunca detalhe interno, FR-054). */
export function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return msg('INTERNAL_ERROR');
}

/** Alerta de erro dentro de uma tela, com "Tentar novamente" opcional. */
export function ErrorAlert({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const offline = error instanceof ApiError && error.status === 0;
  return (
    <div role="alert" className="alert alert--error">
      <Icon name={offline ? 'wifi_off' : 'error'} />
      <div className="alert__text">{errorText(error)}</div>
      {onRetry ? (
        <Button variant="danger" size="compact" onClick={onRetry}>
          Tentar novamente
        </Button>
      ) : null}
    </div>
  );
}

/** Estado de erro de página inteira. */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="page page--narrow">
      <ErrorAlert error={error} onRetry={onRetry} />
    </div>
  );
}
