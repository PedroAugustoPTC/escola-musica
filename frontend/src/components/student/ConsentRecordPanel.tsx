import { useId, useState, type FormEvent } from 'react';
import { hasMessage, msg } from '../../messages/messages';
import { useRecordOwnConsent } from '../../services/consent';
import { fieldErrorsOf } from '../../services/myStudents';
import { todayAtSchool } from '../../services/formatting';
import { Button } from '../Button';
import { ErrorAlert } from '../ErrorState';
import type { StudentFormConsentTerm } from './studentFormTypes';

/**
 * Registro do consentimento do responsável fora do cadastro (FR-031, FR-034): usado no aviso de
 * bloqueio e no detalhe do aluno. O nome de quem consentiu vive só no estado desta tela; a
 * resposta do servidor não o devolve.
 */
export function ConsentRecordPanel({
  studentId,
  term,
  onRecorded,
}: {
  studentId: string;
  term: StudentFormConsentTerm | undefined;
  onRecorded?: () => void;
}) {
  const base = useId();
  const record = useRecordOwnConsent(studentId);
  const [name, setName] = useState('');
  const [relationship, setRelationship] = useState('');
  const [date, setDate] = useState(todayAtSchool());
  const [method, setMethod] = useState('');
  const fieldErrors = fieldErrorsOf(record.error);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!term) return;
    record.mutate(
      {
        consentingName: name,
        relationship: relationship as 'MOTHER',
        consentedOn: date,
        termVersion: term.number,
        collectionMethod: method as 'SIGNED_PAPER',
      },
      {
        onSuccess: () => {
          setName('');
          onRecorded?.();
        },
      },
    );
  };

  return (
    <form className="student-form" onSubmit={submit} noValidate aria-label={msg('CONSENT_RECORD_TITLE')}>
      {record.isError && fieldErrors.length === 0 ? <ErrorAlert error={record.error} fromCatalog /> : null}
      {fieldErrors.length > 0 ? (
        <div role="alert" className="alert alert--error">
          <div className="alert__text">
            <strong>{msg('VALIDATION_FAILED')}</strong>
            <ul>
              {fieldErrors.map((error, index) => (
                <li key={index}>{hasMessage(error.code) ? msg(error.code) : msg('VALIDATION_FAILED')}</li>
              ))}
            </ul>
          </div>
        </div>
      ) : null}
      {term ? (
        <div className="field__help">
          {msg('CONSENT_TERM_CURRENT', {
            number: term.number,
            date: term.publishedOn.split('-').reverse().join('/'),
          })}
          <details>
            <summary>{msg('CONSENT_TERM_READ')}</summary>
            <p className="student-form__term">{term.text}</p>
          </details>
          {term.privacyContact.contact ? (
            <div>
              {msg('CONSENT_PRIVACY_CONTACT', {
                name: term.privacyContact.name,
                contact: term.privacyContact.contact,
              })}
            </div>
          ) : null}
        </div>
      ) : (
        <div className="field__help">{msg('CONSENT_TERM_MISSING')}</div>
      )}
      <div className="field">
        <label htmlFor={`${base}-name`} className="field__label">
          Nome de quem consentiu
        </label>
        <input
          id={`${base}-name`}
          className="input"
          value={name}
          maxLength={120}
          autoComplete="off"
          onChange={(event) => setName(event.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`${base}-relationship`} className="field__label">
          Relação com o aluno
        </label>
        <select
          id={`${base}-relationship`}
          className="input"
          value={relationship}
          onChange={(event) => setRelationship(event.target.value)}
        >
          <option value="">Selecione</option>
          <option value="MOTHER">Mãe</option>
          <option value="FATHER">Pai</option>
          <option value="LEGAL_GUARDIAN">Responsável legal</option>
          <option value="OTHER">Outra relação</option>
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
          value={date}
          onChange={(event) => setDate(event.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`${base}-method`} className="field__label">
          Forma de coleta
        </label>
        <select
          id={`${base}-method`}
          className="input"
          value={method}
          onChange={(event) => setMethod(event.target.value)}
        >
          <option value="">Selecione</option>
          <option value="SIGNED_PAPER">Papel assinado</option>
          <option value="DIGITAL">Digital</option>
          <option value="OTHER">Outra forma</option>
        </select>
      </div>
      <div className="student-form__actions">
        <Button type="submit" size="large" loading={record.isPending} disabled={!term}>
          {msg('CONSENT_RECORD_BUTTON')}
        </Button>
      </div>
    </form>
  );
}
