import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router';
import { Button } from '../../components/Button';
import { ErrorAlert } from '../../components/ErrorState';
import { Icon } from '../../components/Icon';
import { FormErrorSummary, TextField } from '../../components/admin/ui';
import { errorOfField, fieldErrorText, fieldErrorsOf } from '../../components/admin/fieldErrors';
import {
  useConfirmErasure,
  usePreviewErasure,
  useRegisterErasure,
  type ErasurePreview,
  type ErasureRequestInput,
  type ErasureScope,
} from '../../services/admin/erasures';
import { msg } from '../../messages/messages';
import { todayAtSchool } from '../../services/formatting';

export const CONFIRMATION_WORD = 'ELIMINAR';

const ROLES: Array<[ErasureRequestInput['requesterRole'], string]> = [
  ['STUDENT', 'O próprio aluno'],
  ['MOTHER', 'Mãe'],
  ['FATHER', 'Pai'],
  ['LEGAL_GUARDIAN', 'Responsável legal'],
  ['OTHER', 'Outra pessoa'],
];

const VIAS: Array<[ErasureRequestInput['receivedVia'], string]> = [
  ['IN_PERSON', 'Pessoalmente'],
  ['EMAIL', 'E-mail'],
  ['PHONE', 'Telefone'],
  ['LETTER', 'Carta'],
  ['OTHER', 'Outro meio'],
];

/** Rótulo da situação pelo catálogo; situação sem rótulo no catálogo aparece pelo código. */
function statusLabel(status: string): string {
  return status === 'APPROVED' || status === 'CANCELLED' ? msg(`STATUS_${status}`).toLowerCase() : status.toLowerCase();
}

/** Segundos que faltam para o instante (nunca negativo); atualiza a cada segundo. */
function useSecondsLeft(expiresAt: string | undefined): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!expiresAt) return undefined;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [expiresAt]);
  if (!expiresAt) return 0;
  return Math.max(0, Math.ceil((new Date(expiresAt).getTime() - now) / 1000));
}

function clock(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
}

function ScopeLine({ label, byStatus }: { label: string; byStatus: Record<string, number> }) {
  const entries = Object.entries(byStatus).filter(([, count]) => count > 0);
  const total = entries.reduce((sum, [, count]) => sum + count, 0);
  const detail = entries
    .map(([status, count]) => `${count} ${statusLabel(status)}`)
    .join(', ');
  return (
    <li>
      {label}: <strong>{total}</strong>
      {detail ? ` (${detail})` : ''}
    </li>
  );
}

function ScopeSummary({ scope }: { scope: ErasureScope }) {
  return (
    <ul className="admin-list" aria-label="O que será eliminado">
      <ScopeLine label="Relatórios de aula" byStatus={scope.lessonReports} />
      <ScopeLine label="Relatórios semestrais" byStatus={scope.semesterReports} />
      <li>
        Arquivos PDF: <strong>{scope.pdfs}</strong>
      </li>
      <li>
        Associações com professores: <strong>{scope.assignments}</strong>
      </li>
      <li>
        Registros de consentimento: <strong>{scope.consents}</strong>
      </li>
    </ul>
  );
}

/**
 * A17 — Eliminação de dados a pedido, em três passos: pedido, alcance e confirmação. Rota
 * `/admin/students/:studentId/erasure` (pedido novo) ou `/admin/erasure-requests/:requestId/run`
 * (retomar). O token de confirmação vive só em memória.
 */
export function ErasureScreen() {
  const base = useId();
  const navigate = useNavigate();
  const params = useParams();
  const [requestId, setRequestId] = useState<string | undefined>(params.requestId);
  const register = useRegisterErasure(params.studentId);
  const preview = usePreviewErasure(requestId);
  const confirm = useConfirmErasure(requestId);

  const [form, setForm] = useState<ErasureRequestInput>({
    requesterRole: 'MOTHER',
    requesterName: '',
    requestedOn: todayAtSchool(),
    receivedVia: 'IN_PERSON',
    protocolReference: '',
  });
  const [word, setWord] = useState('');
  const [pending, setPending] = useState(false);
  const [shown, setShown] = useState<ErasurePreview | undefined>(undefined);
  const asked = useRef<string | undefined>(undefined);
  const secondsLeft = useSecondsLeft(shown?.expiresAt);
  const fieldErrors = fieldErrorsOf(register.error);
  const previewMutate = preview.mutate;

  // Passo 2: pede o alcance assim que há pedido (uma vez por pedido).
  useEffect(() => {
    if (!requestId || asked.current === requestId) return;
    asked.current = requestId;
    previewMutate(undefined, { onSuccess: setShown });
  }, [requestId, previewMutate]);

  const renew = () => {
    setWord('');
    setPending(false);
    preview.mutate(undefined, { onSuccess: setShown });
  };

  const submitRequest = (event: FormEvent) => {
    event.preventDefault();
    register.mutate(form, { onSuccess: (created) => setRequestId(created.id) });
  };

  const submitConfirmation = (event: FormEvent) => {
    event.preventDefault();
    if (!shown) return;
    confirm.mutate(
      { confirmationToken: shown.confirmationToken, confirmationWord: word },
      {
        onSuccess: (done) => {
          setShown(undefined);
          if (done.status === 'COMPLETED') {
            navigate(`/admin/erasure-requests/${done.id}/receipt`);
          } else {
            setPending(true);
          }
        },
      },
    );
  };

  const set = <K extends keyof ErasureRequestInput>(key: K, value: ErasureRequestInput[K]) =>
    setForm((current) => ({ ...current, [key]: value }));
  const error = (field: string) => {
    const found = errorOfField(fieldErrors, field);
    return found ? fieldErrorText(found.code) : undefined;
  };
  const step = !requestId ? '1 de 3 — pedido' : shown || pending ? '3 de 3 — confirmação' : '2 de 3 — alcance';

  return (
    <div className="page page--medium">
      <div>
        <div className="kicker">Privacidade</div>
        <h1 className="page-title">Eliminar dados do aluno</h1>
        <p className="support-text">Passo {step}. A eliminação não pode ser desfeita.</p>
      </div>

      {!requestId ? (
        <form className="student-form" onSubmit={submitRequest} noValidate>
          {fieldErrors.length > 0 ? (
            <FormErrorSummary messages={fieldErrors.map((item) => fieldErrorText(item.code))} />
          ) : null}
          {register.isError && fieldErrors.length === 0 ? <ErrorAlert error={register.error} fromCatalog /> : null}
          <div className="field">
            <label htmlFor={`${base}-role`} className="field__label">
              Quem pediu
            </label>
            <select
              id={`${base}-role`}
              className="input"
              value={form.requesterRole}
              onChange={(event) => set('requesterRole', event.target.value as ErasureRequestInput['requesterRole'])}
            >
              {ROLES.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <TextField
            label="Nome de quem pediu"
            value={form.requesterName}
            maxLength={255}
            autoComplete="off"
            error={error('requesterName')}
            onChange={(event) => set('requesterName', event.target.value)}
          />
          <TextField
            label="Data do pedido"
            type="date"
            value={form.requestedOn}
            max={todayAtSchool()}
            error={error('requestedOn')}
            onChange={(event) => set('requestedOn', event.target.value)}
          />
          <div className="field">
            <label htmlFor={`${base}-via`} className="field__label">
              Como o pedido chegou
            </label>
            <select
              id={`${base}-via`}
              className="input"
              value={form.receivedVia}
              onChange={(event) => set('receivedVia', event.target.value as ErasureRequestInput['receivedVia'])}
            >
              {VIAS.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <TextField
            label="Protocolo ou referência"
            value={form.protocolReference}
            maxLength={40}
            help="Letras, números, ponto, barra e hífen."
            error={error('protocolReference')}
            onChange={(event) => set('protocolReference', event.target.value)}
          />
          <div className="student-form__actions">
            <Button type="submit" size="large" loading={register.isPending}>
              Continuar
            </Button>
            <Button type="button" variant="secondary" size="large" onClick={() => navigate(-1)}>
              Cancelar
            </Button>
          </div>
        </form>
      ) : null}

      {requestId && preview.isPending ? <p role="status">Calculando o alcance…</p> : null}
      {requestId && preview.isError ? <ErrorAlert error={preview.error} fromCatalog /> : null}

      {shown ? (
        <form className="student-form" onSubmit={submitConfirmation} noValidate>
          <h2 className="card-title">O que será eliminado</h2>
          <ScopeSummary scope={shown.scope} />
          {shown.teachers && shown.teachers.length > 0 ? (
            <p className="support-text">
              Professores com relatórios deste aluno: {shown.teachers.map((teacher) => teacher.name ?? '').join(', ')}.
            </p>
          ) : null}
          <p className="support-text">
            Contato do responsável pela LGPD: {shown.privacyContact.name || 'a definir pela escola'}
            {shown.privacyContact.contact ? ` · ${shown.privacyContact.contact}` : ''}.
          </p>
          {confirm.isError ? <ErrorAlert error={confirm.error} fromCatalog /> : null}
          {secondsLeft > 0 ? (
            <>
              <p role="timer" aria-live="off" className="support-text">
                A confirmação vale por mais <strong>{clock(secondsLeft)}</strong>.
              </p>
              <TextField
                label={`Digite ${CONFIRMATION_WORD} para confirmar`}
                value={word}
                autoComplete="off"
                onChange={(event) => setWord(event.target.value)}
              />
              <div className="student-form__actions">
                <Button
                  type="submit"
                  variant="danger"
                  size="large"
                  icon="delete"
                  loading={confirm.isPending}
                  disabled={word !== CONFIRMATION_WORD}
                >
                  Eliminar dados
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  size="large"
                  onClick={() => navigate('/admin/erasure-requests')}
                >
                  Cancelar
                </Button>
              </div>
            </>
          ) : (
            <div role="alert" className="alert alert--notice">
              <Icon name="schedule" />
              <div className="alert__text">A confirmação venceu. Gere uma nova prévia para continuar.</div>
              <Button type="button" onClick={renew} loading={preview.isPending}>
                Gerar nova prévia
              </Button>
            </div>
          )}
        </form>
      ) : null}

      {pending ? (
        <div role="alert" className="alert alert--notice">
          <Icon name="warning" />
          <div className="alert__text">
            Os dados já saíram do banco, mas alguns arquivos ainda não foram removidos. Repita a eliminação para
            concluir.
          </div>
          <Button type="button" onClick={renew} loading={preview.isPending}>
            Repetir eliminação
          </Button>
        </div>
      ) : null}
    </div>
  );
}
