import { useId, useState, type FormEvent } from 'react';
import { Button } from '../../components/Button';
import { ErrorAlert, ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { errorOfField, fieldErrorText, fieldErrorsOf } from '../../components/admin/fieldErrors';
import { useConsentTerms, usePublishConsentTerm } from '../../services/admin/consents';
import { todayAtSchool } from '../../services/formatting';

/** A16d — Versões do termo de consentimento; publicar uma versão nova exige confirmação. */
export function ConsentTermsScreen() {
  const base = useId();
  const terms = useConsentTerms();
  const publish = usePublishConsentTerm();
  const [text, setText] = useState('');
  const [requiresNew, setRequiresNew] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const fieldErrors = fieldErrorsOf(publish.error);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!confirming) {
      setConfirming(true);
      return;
    }
    publish.mutate(
      { publishedOn: todayAtSchool(), text, requiresNewConsent: requiresNew },
      {
        onSuccess: () => {
          setText('');
          setRequiresNew(false);
          setConfirming(false);
        },
        onError: () => setConfirming(false),
      },
    );
  };

  const textError = errorOfField(fieldErrors, 'text');

  return (
    <div className="page page--medium">
      <div>
        <div className="kicker">Consentimento</div>
        <h1 className="page-title">Termos de consentimento</h1>
      </div>
      {terms.isPending ? <LoadingState label="Carregando termos" rows={2} /> : null}
      {terms.isError ? <ErrorState error={terms.error} onRetry={() => void terms.refetch()} /> : null}
      {terms.data ? (
        <ul className="admin-list" aria-label="Versões do termo">
          {terms.data.map((term) => (
            <li key={term.number} className="admin-list__item">
              <details>
                <summary>
                  Versão {term.number} · {term.publishedOn.split('-').reverse().join('/')}
                  {term.current ? ' · vigente' : ''}
                  {term.requiresNewConsent ? ' · exige novo consentimento' : ''}
                </summary>
                <p className="student-form__term">{term.text}</p>
              </details>
            </li>
          ))}
        </ul>
      ) : null}

      <form className="student-form" onSubmit={submit} noValidate>
        <h2 className="card-title">Publicar nova versão</h2>
        {publish.isError && fieldErrors.length === 0 ? <ErrorAlert error={publish.error} fromCatalog /> : null}
        <div className="field">
          <label htmlFor={`${base}-text`} className="field__label">
            Texto do termo
          </label>
          <textarea
            id={`${base}-text`}
            className="input"
            rows={8}
            maxLength={20000}
            value={text}
            onChange={(event) => {
              setText(event.target.value);
              setConfirming(false);
            }}
            aria-invalid={textError ? true : undefined}
          />
          {textError ? <div className="field__error">{fieldErrorText(textError.code)}</div> : null}
        </div>
        <label className="student-form__checkbox">
          <input
            type="checkbox"
            checked={requiresNew}
            onChange={(event) => {
              setRequiresNew(event.target.checked);
              setConfirming(false);
            }}
          />
          <span>Exige novo consentimento dos responsáveis</span>
        </label>
        {confirming ? (
          <div role="alert" className="alert alert--notice">
            <div className="alert__text">
              {requiresNew
                ? 'Ao publicar, os consentimentos de versões anteriores deixam de valer e os alunos menores ficam pendentes até um novo registro. Confirme para publicar.'
                : 'Esta versão é compatível com as anteriores: os consentimentos já registrados continuam válidos. Confirme para publicar.'}
            </div>
          </div>
        ) : null}
        <div className="student-form__actions">
          <Button type="submit" size="large" loading={publish.isPending} disabled={text.trim() === ''}>
            {confirming ? 'Confirmar publicação' : 'Publicar versão'}
          </Button>
        </div>
      </form>
    </div>
  );
}
