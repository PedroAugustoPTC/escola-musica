import { useEffect, useId, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorState';
import { Icon } from '../../components/Icon';
import { LoadingState } from '../../components/LoadingState';
import { errorOfField, fieldErrorText, fieldErrorsOf } from '../../components/admin/fieldErrors';
import { TextField } from '../../components/admin/ui';
import { hasMessage, msg } from '../../messages/messages';
import {
  useAuditIntegrity,
  useAuditTrail,
  useVerifyAuditIntegrity,
  type AuditEvent,
  type AuditIntegrity,
  type AuditTrailFilters,
} from '../../services/admin/auditTrail';
import { instantDate, instantTime, todayAtSchool } from '../../services/formatting';

const DEFAULT_DAYS = 7;
const MAX_DAYS_BETWEEN = 89;
const RESULTS = ['SUCCESS', 'FAILURE', 'DENIED', 'BLOCKED'] as const;
const ORIGINS = ['WEB', 'TELEGRAM', 'SYSTEM_STARTUP', 'INITIAL_LOAD', 'RETENTION', 'ALERT_EVALUATION'] as const;

/** Soma dias a uma data civil `AAAA-MM-DD` (sem fuso: só calendário). */
function addDays(day: string, amount: number): string {
  const date = new Date(`${day}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + amount);
  return date.toISOString().slice(0, 10);
}

function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T12:00:00Z`) - Date.parse(`${from}T12:00:00Z`)) / 86_400_000);
}

/** Filtros da rota; sem período na rota, vale o padrão dos últimos 7 dias (FR-045). */
function filtersOf(params: URLSearchParams): AuditTrailFilters {
  const to = params.get('to') || todayAtSchool();
  return {
    from: params.get('from') || addDays(to, -(DEFAULT_DAYS - 1)),
    to,
    operation: params.get('operation') || undefined,
    result: params.get('result') || undefined,
    origin: params.get('origin') || undefined,
    targetId: params.get('targetId') || undefined,
    page: Math.max(0, Number(params.get('page')) || 0),
  };
}

function eventLabel(operation: string): string {
  const key = `AUDIT_EVENT_${operation}`;
  return hasMessage(key) ? msg(key) : operation;
}

function actorText(event: AuditEvent): string {
  if (event.actorRole === 'SYSTEM') return msg('AUDIT_ACTOR_SYSTEM');
  return event.actorLabel ?? msg('AUDIT_ACTOR_UNKNOWN');
}

function detailText(event: AuditEvent): string {
  const parts: string[] = [];
  if (event.documentVersion != null) parts.push(msg('AUDIT_VERSION', { version: event.documentVersion }));
  if (event.itemCount != null) parts.push(msg('AUDIT_ITEMS', { count: event.itemCount }));
  return parts.join(' · ');
}

/** Selo de integridade: texto e ícone, nunca só cor (WCAG 1.4.1). */
function IntegritySeal({ integrity }: { integrity: AuditIntegrity }) {
  const broken = integrity.status === 'BROKEN';
  const never = integrity.status === 'NEVER_VERIFIED';
  const label = broken
    ? msg('AUDIT_INTEGRITY_BROKEN')
    : never
      ? msg('AUDIT_INTEGRITY_NEVER')
      : msg('AUDIT_INTEGRITY_INTACT');
  return (
    <div className={`alert alert--${broken ? 'error' : 'info'}`} role={broken ? 'alert' : 'status'}>
      <Icon name={broken ? 'error' : never ? 'info' : 'verified_user'} />
      <div className="alert__text">
        <strong>{label}</strong>
        {!never ? (
          <>
            {' · '}
            {msg('AUDIT_INTEGRITY_CHECKED', { count: integrity.checkedEvents })}
            {integrity.verifiedAt
              ? ` · ${msg('AUDIT_INTEGRITY_VERIFIED_AT', {
                  when: `${instantDate(integrity.verifiedAt)} ${instantTime(integrity.verifiedAt)}`,
                })}`
              : ''}
          </>
        ) : null}
        {broken && integrity.firstBrokenSequence != null
          ? ` · ${msg('AUDIT_INTEGRITY_FIRST_BROKEN', { sequence: integrity.firstBrokenSequence })}`
          : null}
      </div>
    </div>
  );
}

/** A14 — Trilha de auditoria: quem fez o quê, quando e por qual canal; somente leitura. */
export function AuditTrailScreen() {
  const [params, setParams] = useSearchParams();
  const filters = filtersOf(params);
  const [draft, setDraft] = useState({
    from: filters.from,
    to: filters.to,
    operation: filters.operation ?? '',
    result: filters.result ?? '',
    origin: filters.origin ?? '',
  });
  const [localError, setLocalError] = useState<string | null>(null);
  const operationId = useId();
  const resultId = useId();
  const originId = useId();
  const audit = useAuditTrail(filters);
  const integrity = useAuditIntegrity();
  const verify = useVerifyAuditIntegrity();
  const shownIntegrity = verify.data ?? integrity.data;
  const data = audit.data;
  const fieldErrors = fieldErrorsOf(audit.error);
  const periodError = errorOfField(fieldErrors, 'period');
  const listStart = useRef<HTMLDivElement>(null);
  const firstRender = useRef(true);

  // ao trocar de página o foco vai para o início da lista
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    listStart.current?.focus();
  }, [filters.page]);

  const update = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === undefined || value === '') next.delete(key);
      else next.set(key, value);
    }
    if (!('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  };

  const apply = () => {
    if (!draft.from || !draft.to) {
      setLocalError(msg('PERIOD_REQUIRED'));
      return;
    }
    if (draft.from > draft.to) {
      setLocalError(msg('PERIOD_START_AFTER_END'));
      return;
    }
    if (daysBetween(draft.from, draft.to) > MAX_DAYS_BETWEEN) {
      setLocalError(msg('AUDIT_PERIOD_TOO_LONG'));
      return;
    }
    setLocalError(null);
    update({
      from: draft.from,
      to: draft.to,
      operation: draft.operation,
      result: draft.result,
      origin: draft.origin,
    });
  };

  const clear = () => {
    const to = todayAtSchool();
    const from = addDays(to, -(DEFAULT_DAYS - 1));
    setDraft({ from, to, operation: '', result: '', origin: '' });
    setLocalError(null);
    update({ from: undefined, to: undefined, operation: undefined, result: undefined, origin: undefined });
  };

  const periodMessage = localError ?? (periodError ? fieldErrorText(periodError.code) : undefined);

  return (
    <div className="page page--wide">
      <div>
        <h1 className="page-title">{msg('AUDIT_TITLE')}</h1>
        <p className="subtitle">{msg('AUDIT_SUBTITLE')}</p>
      </div>

      <section aria-label={msg('AUDIT_INTEGRITY_TITLE')} className="audit-trail__integrity">
        {shownIntegrity ? <IntegritySeal integrity={shownIntegrity} /> : null}
        <Button
          variant="secondary"
          size="compact"
          icon="verified_user"
          disabled={verify.isPending}
          onClick={() => verify.mutate(undefined, { onSuccess: () => void audit.refetch() })}
        >
          {verify.isPending ? msg('AUDIT_INTEGRITY_VERIFYING') : msg('AUDIT_INTEGRITY_VERIFY_NOW')}
        </Button>
      </section>

      <section className="school-history__filters" aria-label="Filtros">
        <form
          className="school-history__filters-form"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            apply();
          }}
        >
          <TextField
            label={msg('AUDIT_FILTER_FROM')}
            type="date"
            value={draft.from}
            error={periodMessage}
            help={msg('AUDIT_PERIOD_HINT')}
            onChange={(event) => setDraft({ ...draft, from: event.target.value })}
          />
          <TextField
            label={msg('AUDIT_FILTER_TO')}
            type="date"
            value={draft.to}
            onChange={(event) => setDraft({ ...draft, to: event.target.value })}
          />
          <div className="field">
            <label htmlFor={operationId} className="field__label">
              {msg('AUDIT_FILTER_OPERATION')}
            </label>
            <select
              id={operationId}
              className="input"
              value={draft.operation}
              onChange={(event) => setDraft({ ...draft, operation: event.target.value })}
            >
              <option value="">{msg('AUDIT_FILTER_ALL')}</option>
              {OPERATIONS.map((operation) => (
                <option key={operation} value={operation}>
                  {eventLabel(operation)}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor={resultId} className="field__label">
              {msg('AUDIT_FILTER_RESULT')}
            </label>
            <select
              id={resultId}
              className="input"
              value={draft.result}
              onChange={(event) => setDraft({ ...draft, result: event.target.value })}
            >
              <option value="">{msg('AUDIT_FILTER_ALL')}</option>
              {RESULTS.map((result) => (
                <option key={result} value={result}>
                  {msg(`AUDIT_RESULT_${result}`)}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor={originId} className="field__label">
              {msg('AUDIT_FILTER_ORIGIN')}
            </label>
            <select
              id={originId}
              className="input"
              value={draft.origin}
              onChange={(event) => setDraft({ ...draft, origin: event.target.value })}
            >
              <option value="">{msg('AUDIT_FILTER_ALL')}</option>
              {ORIGINS.map((origin) => (
                <option key={origin} value={origin}>
                  {msg(`AUDIT_ORIGIN_${origin}`)}
                </option>
              ))}
            </select>
          </div>
          <div className="admin-actions school-history__filters-actions">
            <Button type="submit" size="compact">
              {msg('AUDIT_APPLY')}
            </Button>
            <Button variant="secondary" size="compact" onClick={clear}>
              {msg('AUDIT_CLEAR')}
            </Button>
          </div>
        </form>
      </section>

      <div ref={listStart} tabIndex={-1} className="school-history__start">
        {audit.isPending ? <LoadingState label={msg('AUDIT_LOADING')} /> : null}
        {audit.isError && fieldErrors.length === 0 ? (
          <ErrorAlert error={audit.error} onRetry={() => void audit.refetch()} />
        ) : null}

        {data && !audit.isError && data.items.length === 0 ? (
          <EmptyState icon="history" text={msg('AUDIT_EMPTY')} />
        ) : null}

        {data && !audit.isError && data.items.length > 0 ? (
          <div className="school-history__results" aria-busy={audit.isFetching || undefined}>
            <p role="status" className="support-text">
              {msg('AUDIT_TOTAL', { total: data.total })}
            </p>
            <table className="school-history__table">
              <caption className="school-history__caption">{msg('AUDIT_TITLE')}</caption>
              <thead>
                <tr>
                  <th scope="col">{msg('AUDIT_COLUMN_WHEN')}</th>
                  <th scope="col">{msg('AUDIT_COLUMN_WHO')}</th>
                  <th scope="col">{msg('AUDIT_COLUMN_CHANNEL')}</th>
                  <th scope="col">{msg('AUDIT_COLUMN_EVENT')}</th>
                  <th scope="col">{msg('AUDIT_COLUMN_TARGET')}</th>
                  <th scope="col">{msg('AUDIT_COLUMN_RESULT')}</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((event) => (
                  <tr key={event.id}>
                    <td>
                      {instantDate(event.occurredAt)} {instantTime(event.occurredAt)}
                    </td>
                    <td>{actorText(event)}</td>
                    <td>{msg(`AUDIT_ORIGIN_${event.origin}`)}</td>
                    <td>
                      {eventLabel(event.operation)}
                      {detailText(event) ? <span className="support-text"> · {detailText(event)}</span> : null}
                    </td>
                    <td>{event.targetLabel ?? ''}</td>
                    <td>
                      {msg(`AUDIT_RESULT_${event.result}`)}
                      {event.errorCode ? <span className="support-text"> · {event.errorCode}</span> : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {data.total > data.size ? (
              <nav aria-label="Paginação" className="admin-pagination">
                <span className="support-text">
                  {data.page * data.size + 1}–{Math.min((data.page + 1) * data.size, data.total)} de {data.total}
                </span>
                <div className="admin-actions">
                  <Button
                    variant="secondary"
                    size="compact"
                    disabled={data.page <= 0}
                    onClick={() => update({ page: data.page > 1 ? String(data.page - 1) : undefined })}
                  >
                    Anterior
                  </Button>
                  <Button
                    variant="secondary"
                    size="compact"
                    disabled={(data.page + 1) * data.size >= data.total}
                    onClick={() => update({ page: String(data.page + 1) })}
                  >
                    Próxima
                  </Button>
                </div>
              </nav>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  );
}

/** Eventos oferecidos no filtro (os da lista do contrato da trilha). */
const OPERATIONS = [
  'ADMIN_SIGN_IN',
  'ADMIN_ACTIVATE',
  'ADMIN_SIGN_OUT',
  'ADMIN_CHANGE_PASSWORD',
  'TEACHER_SIGN_IN',
  'TEACHER_SIGN_OUT',
  'ATTEMPT_BLOCKED',
  'AUTHORIZATION_DENIED',
  'TEACHER_REGISTER',
  'TEACHER_UPDATE',
  'STUDENT_REGISTER',
  'STUDENT_UPDATE',
  'ASSIGNMENT_ASSIGN',
  'ASSIGNMENT_REMOVE',
  'LESSON_REPORT_APPROVE',
  'LESSON_REPORT_CANCEL',
  'SEMESTER_REPORT_APPROVE',
  'SEMESTER_REPORT_CANCEL',
  'HISTORY_QUERY',
  'REPORT_DOCUMENT_OPEN',
  'REPORT_DOCUMENT_DOWNLOAD',
  'REPORT_DOCUMENT_SENT',
  'SCHOOL_HISTORY_QUERY',
  'SCHOOL_REPORT_DOCUMENT_OPEN',
  'SCHOOL_REPORT_DOCUMENT_DOWNLOAD',
  'AUDIT_TRAIL_QUERY',
  'AUDIT_TRAIL_VERIFY',
] as const;
