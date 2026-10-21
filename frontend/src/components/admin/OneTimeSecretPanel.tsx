import { useEffect, useRef, useState } from 'react';
import { useBlocker } from 'react-router';
import { msg } from '../../messages/messages';
import { Button } from '../Button';
import { ConfirmationDialog } from '../ConfirmationDialog';
import { Icon } from '../Icon';
import type { OneTimeSecretPanelProps } from './oneTimeSecretPanelTypes';

const SCHOOL_TIME_ZONE = 'America/Sao_Paulo';

const dateTimeFormat = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: SCHOOL_TIME_ZONE,
});

/** "28/09/2026 às 09:00" no fuso da escola. */
export function formatSchoolDateTime(instant: string): string {
  const parts = Object.fromEntries(
    dateTimeFormat.formatToParts(new Date(instant)).map((part) => [part.type, part.value]),
  );
  return `${parts.day}/${parts.month}/${parts.year} às ${parts.hour}:${parts.minute}`;
}

/**
 * Código exibido uma única vez (acesso do professor ou ativação do administrador).
 *
 * O valor vive só em `useState`: nunca em cache de consulta, `localStorage`, URL ou título da página.
 * "Já anotei o código" apaga o valor da memória e libera a navegação; enquanto o código está na tela,
 * sair da página (rota ou recarga) pede confirmação, porque ele não pode ser exibido de novo.
 */
export function OneTimeSecretPanel({
  kind,
  secret,
  recipientName,
  validUntil,
  onAcknowledge,
}: OneTimeSecretPanelProps) {
  const [value, setValue] = useState<string | null>(secret);
  const [copyNotice, setCopyNotice] = useState('');
  const shown = useRef(true);
  const blocker = useBlocker(() => shown.current);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!shown.current) return;
      event.preventDefault();
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, []);

  const copy = async () => {
    if (value === null) return;
    try {
      await navigator.clipboard.writeText(value);
      setCopyNotice(msg('ADMIN_SECRET_COPIED'));
    } catch {
      setCopyNotice('Não foi possível copiar. Selecione o código e copie à mão.');
    }
  };

  const acknowledge = () => {
    shown.current = false;
    setValue(null);
    setCopyNotice('');
    onAcknowledge();
  };

  if (value === null) return null;

  const title = kind === 'access-code' ? 'Código de acesso de' : 'Código de ativação de';

  return (
    <section className="secret-panel" aria-labelledby="secret-panel-title">
      <h2 id="secret-panel-title" className="card-title">
        {title} {recipientName}
      </h2>
      <p className="support-text">Válido até {formatSchoolDateTime(validUntil)}.</p>
      <p className="secret-panel__code" translate="no">
        {value}
      </p>
      <div className="alert alert--notice">
        <Icon name="warning" />
        <div className="alert__text">{msg('ADMIN_SECRET_SHOWN_ONCE')}</div>
      </div>
      <div role="status" className="support-text">
        {copyNotice}
      </div>
      <div className="secret-panel__actions">
        <Button variant="secondary" icon="content_copy" onClick={() => void copy()}>
          Copiar
        </Button>
        <Button icon="check" onClick={acknowledge}>
          {msg('ADMIN_SECRET_ACKNOWLEDGE')}
        </Button>
      </div>
      <ConfirmationDialog
        open={blocker.state === 'blocked'}
        title="Sair sem anotar o código?"
        text="O código não aparece de novo. Se sair agora, será preciso reemitir um código novo."
        icon="warning"
        danger
        confirmLabel="Sair mesmo assim"
        backLabel="Voltar e anotar"
        onConfirm={() => blocker.proceed?.()}
        onClose={() => blocker.reset?.()}
      />
    </section>
  );
}
