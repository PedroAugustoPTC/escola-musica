import { useId, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { Button } from '../Button';
import { ErrorAlert } from '../ErrorState';
import { LoadingState } from '../LoadingState';
import {
  useConsentTerms,
  useRecordConsent,
  useRevokeConsent,
  useStudentConsents,
  type ConsentSummary,
  type RevokerRole,
} from '../../services/admin/consents';
import { todayAtSchool } from '../../services/formatting';
import { errorOfField, fieldErrorText, fieldErrorsOf } from './fieldErrors';

const STATUS_TEXT: Record<string, string> = {
  VALID: 'Válido',
  REVOKED: 'Revogado',
  SUPERSEDED: 'Termo superado',
};
const RELATION_TEXT: Record<string, string> = {
  MOTHER: 'Mãe',
  FATHER: 'Pai',
  LEGAL_GUARDIAN: 'Responsável legal',
  OTHER: 'Outra relação',
};
const METHOD_TEXT: Record<string, string> = {
  SIGNED_PAPER: 'Papel assinado',
  DIGITAL: 'Digital',
  OTHER: 'Outra forma',
};
const REVOKER_TEXT: Record<RevokerRole, string> = {
  MOTHER: 'Mãe',
  FATHER: 'Pai',
  LEGAL_GUARDIAN: 'Responsável legal',
  OTHER: 'Outra pessoa',
  STUDENT: 'O próprio aluno',
};

function day(date: string): string {
  return date.split('-').reverse().join('/');
}

/** Situação atual a partir do histórico: qualquer registro válido vence; sem registro, ausente. */
function currentStatus(history: ConsentSummary[]): string {
  if (history.length === 0) return 'Ausente';
  if (history.some((item) => item.status === 'VALID')) return 'Válido';
  if (history.some((item) => item.status === 'REVOKED')) return 'Revogado';
  return 'Termo superado';
}

/**
 * A16 — Consentimento do responsável no detalhe do aluno (somente o administrador): situação atual,
 * histórico, registrar um consentimento novo e registrar revogação (com confirmação).
 */
export function StudentConsentSection({ studentId }: { studentId: string }) {
  const base = useId();
  const history = useStudentConsents(studentId);
  const terms = useConsentTerms();
  const record = useRecordConsent(studentId);
  const revoke = useRevokeConsent();
  const [open, setOpen] = useState(false);
  const [revoking, setRevoking] = useState<string | null>(null);
  const [confirmingRevocation, setConfirmingRevocation] = useState(false);
  const [revokedBy, setRevokedBy] = useState<RevokerRole>('MOTHER');
  const [revokedOn, setRevokedOn] = useState(todayAtSchool());
  const [form, setForm] = useState({ name: '', relationship: '', date: '', method: '' });
  const current = terms.data?.[0];
  const fieldErrors = fieldErrorsOf(record.error);
  const error = (field: string) => errorOfField(fieldErrors, field);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!current) return;
    record.mutate(
      {
        consentingName: form.name.trim(),
        relationship: form.relationship as 'MOTHER',
        consentedOn: form.date,
        termVersion: current.number,
        collectionMethod: form.method as 'DIGITAL',
      },
      {
        onSuccess: () => {
          record.reset();
          setForm({ name: '', relationship: '', date: '', method: '' });
          setOpen(false);
        },
      },
    );
  };

  return (
    <section className="admin-section" aria-labelledby={`${base}-title`}>
      <h2 id={`${base}-title`} className="card-title">
        Consentimento do responsável
      </h2>
      {history.isPending ? <LoadingState label="Carregando consentimentos" rows={2} /> : null}
      {history.isError ? <ErrorAlert error={history.error} fromCatalog /> : null}
      {history.data ? (
        <>
          <p>
            <strong>Situação: </strong>
            {currentStatus(history.data)}
          </p>
          {history.data.length > 0 ? (
            <ul className="admin-list" aria-label="Histórico de consentimentos">
              {history.data.map((item) => (
                <li key={item.id} className="admin-list__item">
                  <div>
                    <strong>{STATUS_TEXT[item.status] ?? item.status}</strong> · termo v{item.termVersion} ·{' '}
                    {day(item.consentedOn)} · {RELATION_TEXT[item.relationship] ?? item.relationship} ·{' '}
                    {METHOD_TEXT[item.collectionMethod] ?? item.collectionMethod}
                    {item.consentingName ? <div className="support-text">{item.consentingName}</div> : null}
                    {item.revokedOn ? <div className="support-text">Revogado em {day(item.revokedOn)}</div> : null}
                  </div>
                  {item.status !== 'REVOKED' ? (
                    <Button
                      variant="secondary"
                      size="compact"
                      onClick={() => {
                        setRevoking(item.id);
                        setConfirmingRevocation(false);
                      }}
                    >
                      Registrar revogação
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          ) : null}
        </>
      ) : null}

      {revoking ? (
        <div role="group" aria-label="Revogação do consentimento" className="card">
          {revoke.isError ? <ErrorAlert error={revoke.error} fromCatalog /> : null}
          <div className="field">
            <label htmlFor={`${base}-revoked-on`} className="field__label">
              Data da revogação
            </label>
            <input
              id={`${base}-revoked-on`}
              className="input"
              type="date"
              value={revokedOn}
              onChange={(event) => setRevokedOn(event.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor={`${base}-revoked-by`} className="field__label">
              Quem revogou
            </label>
            <select
              id={`${base}-revoked-by`}
              className="input"
              value={revokedBy}
              onChange={(event) => setRevokedBy(event.target.value as RevokerRole)}
            >
              {Object.entries(REVOKER_TEXT).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          {confirmingRevocation ? (
            <div role="alert" className="alert alert--notice">
              <div className="alert__text">
                Com a revogação, este aluno fica sem consentimento válido e a geração de relatórios por IA é
                bloqueada até um novo registro. Confirme para registrar.
              </div>
            </div>
          ) : null}
          <div className="student-form__actions">
            <Button
              loading={revoke.isPending}
              onClick={() => {
                if (!confirmingRevocation) {
                  setConfirmingRevocation(true);
                  return;
                }
                revoke.mutate(
                  { consentId: revoking, revokedOn, revokedBy },
                  { onSuccess: () => setRevoking(null) },
                );
              }}
            >
              {confirmingRevocation ? 'Confirmar revogação' : 'Registrar revogação'}
            </Button>
            <Button variant="secondary" onClick={() => setRevoking(null)}>
              Cancelar
            </Button>
          </div>
        </div>
      ) : null}

      {open ? (
        <form className="student-form" onSubmit={submit} noValidate>
          <h3 className="card-title">Registrar consentimento</h3>
          {record.isError && fieldErrors.length === 0 ? <ErrorAlert error={record.error} fromCatalog /> : null}
          {current ? (
            <p className="support-text">Termo vigente: versão {current.number}.</p>
          ) : (
            <p className="support-text">
              Ainda não há termo publicado. <Link to="/admin/consent-terms">Publicar o termo</Link>
            </p>
          )}
          <div className="field">
            <label htmlFor={`${base}-name`} className="field__label">
              Nome de quem consentiu
            </label>
            <input
              id={`${base}-name`}
              className="input"
              value={form.name}
              maxLength={120}
              autoComplete="off"
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              aria-invalid={error('consent.consentingName') || error('consentingName') ? true : undefined}
            />
            {error('consentingName') ? (
              <div className="field__error">{fieldErrorText(error('consentingName')!.code)}</div>
            ) : null}
          </div>
          <div className="field">
            <label htmlFor={`${base}-relationship`} className="field__label">
              Relação com o aluno
            </label>
            <select
              id={`${base}-relationship`}
              className="input"
              value={form.relationship}
              onChange={(event) => setForm({ ...form, relationship: event.target.value })}
            >
              <option value="">Selecione</option>
              {Object.entries(RELATION_TEXT).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor={`${base}-date`} className="field__label">
              Data do consentimento
            </label>
            <input
              id={`${base}-date`}
              className="input"
              type="date"
              value={form.date}
              onChange={(event) => setForm({ ...form, date: event.target.value })}
            />
          </div>
          <div className="field">
            <label htmlFor={`${base}-method`} className="field__label">
              Forma de coleta
            </label>
            <select
              id={`${base}-method`}
              className="input"
              value={form.method}
              onChange={(event) => setForm({ ...form, method: event.target.value })}
            >
              <option value="">Selecione</option>
              {Object.entries(METHOD_TEXT).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div className="student-form__actions">
            <Button type="submit" loading={record.isPending} disabled={!current}>
              Registrar consentimento
            </Button>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
          </div>
        </form>
      ) : (
        <Button variant="secondary" onClick={() => setOpen(true)}>
          Registrar consentimento
        </Button>
      )}
    </section>
  );
}
