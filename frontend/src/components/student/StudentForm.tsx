import { useEffect, useId, useRef, useState, type FormEvent, type MouseEvent } from 'react';
import { hasMessage, msg } from '../../messages/messages';
import { Button } from '../Button';
import { Icon } from '../Icon';
import type { StudentFormFieldError, StudentFormProps, StudentFormValues } from './studentFormTypes';
import '../../styles/student-registry.css';

type FieldKey =
  | 'name'
  | 'birthDate'
  | 'cpf'
  | 'instrument'
  | 'guardian'
  | 'guardian.name'
  | 'guardian.phone'
  | 'guardian.email'
  | 'teacherIds'
  | 'consent'
  | 'consent.consentingName'
  | 'consent.relationship'
  | 'consent.consentedOn'
  | 'consent.collectionMethod'
  | 'consent.termVersion';

const FIELD_ALIASES: Record<string, FieldKey> = {
  name: 'name',
  birthDate: 'birthDate',
  cpf: 'cpf',
  instrument: 'instrument',
  guardian: 'guardian',
  'guardian.name': 'guardian.name',
  guardianName: 'guardian.name',
  'guardian.phone': 'guardian.phone',
  guardianPhone: 'guardian.phone',
  'guardian.email': 'guardian.email',
  guardianEmail: 'guardian.email',
  teacherIds: 'teacherIds',
  consent: 'consent',
  'consent.consentingName': 'consent.consentingName',
  'consent.relationship': 'consent.relationship',
  'consent.consentedOn': 'consent.consentedOn',
  'consent.collectionMethod': 'consent.collectionMethod',
  'consent.termVersion': 'consent.termVersion',
};

const FIELD_LABELS: Record<FieldKey, string> = {
  name: 'Nome do aluno',
  birthDate: 'Data de nascimento',
  cpf: 'CPF',
  instrument: 'Instrumento',
  guardian: 'Responsável',
  'guardian.name': 'Nome do responsável',
  'guardian.phone': 'Telefone do responsável',
  'guardian.email': 'E-mail do responsável',
  teacherIds: 'Professores',
  consent: 'Consentimento do responsável',
  'consent.consentingName': 'Nome de quem consentiu',
  'consent.relationship': 'Relação com o aluno',
  'consent.consentedOn': 'Data do consentimento',
  'consent.collectionMethod': 'Forma de coleta',
  'consent.termVersion': 'Versão do termo',
};

/** Texto do catálogo para o código do erro; código desconhecido cai na mensagem geral de validação. */
function errorText(code: string): string {
  return hasMessage(code) ? msg(code) : msg('VALIDATION_FAILED');
}

/** Só apresentação: a data indica menor de 18 anos hoje? A regra de verdade é do servidor. */
function isMinorDate(birthDate: string): boolean {
  if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(birthDate)) return false;
  const today = new Date();
  const eighteen = new Date(
    Number(birthDate.slice(0, 4)) + 18,
    Number(birthDate.slice(5, 7)) - 1,
    Number(birthDate.slice(8, 10)),
  );
  return eighteen.getTime() > today.getTime();
}

function initialValues(props: StudentFormProps): StudentFormValues {
  const student = props.student;
  return {
    name: student?.name ?? '',
    birthDate: student?.birthDate ?? '',
    // na edição o CPF começa em branco (em branco mantém o atual); o CPF nunca vem da API
    cpf: '',
    instrument: student?.instrument ?? '',
    guardianName: student?.guardian?.name ?? '',
    guardianPhone: student?.guardian?.phone ?? '',
    guardianEmail: student?.guardian?.email ?? '',
    teacherIds: student?.teachers?.map((teacher) => teacher.id) ?? [],
    consentingName: '',
    consentRelationship: '',
    consentedOn: '',
    consentMethod: '',
    consentTermVersion: props.consentTerm?.number ?? null,
  };
}

/**
 * Formulário de aluno compartilhado por A11/A12 (administrador) e P02/P03 (professor), conforme
 * `contracts/portal-screens.md` §3 e §4. Só valida a forma (tamanho máximo); as regras de negócio
 * (CPF, data, responsável de menor, unicidade) vêm do servidor em `details.fields`.
 */
export function StudentForm(props: StudentFormProps) {
  const { variant, mode, teacherOptions, fieldErrors, submitting, blockedSeconds, onSubmit, onCancel } =
    props;
  const base = useId();
  const [values, setValues] = useState<StudentFormValues>(() => initialValues(props));
  const [attempt, setAttempt] = useState(0);
  const summary = useRef<HTMLDivElement>(null);

  const signature = fieldErrors.map((error) => `${error.field}:${error.code}`).join('|');
  useEffect(() => {
    if (signature) summary.current?.focus();
  }, [signature, attempt]);

  const blocked = blockedSeconds !== null;
  const id = (key: string) => `${base}-${key}`;

  const errorsByKey = new Map<FieldKey, StudentFormFieldError[]>();
  for (const error of fieldErrors) {
    const key = FIELD_ALIASES[error.field];
    if (!key) continue;
    errorsByKey.set(key, [...(errorsByKey.get(key) ?? []), error]);
  }

  const errorIds = (key: FieldKey) =>
    (errorsByKey.get(key) ?? []).map((_, index) => id(`${key}-error-${index}`));

  const set = <K extends keyof StudentFormValues>(key: K, value: StudentFormValues[K]) =>
    setValues((current) => ({ ...current, [key]: value }));

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (submitting || blocked) return;
    setAttempt((count) => count + 1);
    onSubmit({ ...values, consentTermVersion: props.consentTerm?.number ?? null });
  };

  const focusField = (key: FieldKey) => (event: MouseEvent) => {
    event.preventDefault();
    const target = key === 'guardian' ? 'guardian.name' : key === 'teacherIds' ? 'teacherIds' : key;
    document.getElementById(id(target))?.focus();
  };

  const fieldErrorsFor = (key: FieldKey) =>
    (errorsByKey.get(key) ?? []).map((error, index) => (
      <div key={index} className="field__error">
        <Icon name="error" size="small" />
        <span id={id(`${key}-error-${index}`)}>{errorText(error.code)}</span>
      </div>
    ));

  interface TextFieldOptions {
    key: Exclude<FieldKey, 'guardian' | 'teacherIds'>;
    value: string;
    onChange: (value: string) => void;
    type?: string;
    maxLength?: number;
    hintId?: string;
    hint?: string;
    extra?: Record<string, string | boolean | undefined>;
  }

  const textField = ({
    key,
    value,
    onChange,
    type = 'text',
    maxLength,
    hintId,
    hint,
    extra,
  }: TextFieldOptions) => {
    const invalid = (errorsByKey.get(key) ?? []).length > 0;
    const described = [...errorIds(key), ...(hintId ? [hintId] : [])].join(' ') || undefined;
    return (
      <div className="field">
        <label htmlFor={id(key)} className="field__label">
          {FIELD_LABELS[key]}
        </label>
        <input
          id={id(key)}
          className="input"
          type={type}
          value={value}
          maxLength={maxLength}
          onChange={(event) => onChange(event.target.value)}
          aria-invalid={invalid ? true : undefined}
          aria-describedby={described}
          autoComplete="off"
          {...extra}
        />
        {fieldErrorsFor(key)}
        {hint && hintId ? (
          <div id={hintId} className="field__help">
            {hint}
          </div>
        ) : null}
      </div>
    );
  };

  const cpfHintId = id('cpf-hint');
  // só decide se a seção aparece; a exigência e a validade são do servidor
  const showConsent = isMinorDate(values.birthDate);
  const guardianGroupErrors = errorsByKey.get('guardian') ?? [];
  const guardianDescribedBy = errorIds('guardian').join(' ') || undefined;

  return (
    <form className="student-form" onSubmit={submit} noValidate>
      {fieldErrors.length > 0 ? (
        <div ref={summary} role="alert" tabIndex={-1} className="alert alert--error student-form__summary">
          <Icon name="error" />
          <div className="alert__text">
            <strong>{msg('VALIDATION_FAILED')}</strong>
            <ul>
              {fieldErrors.map((error, index) => {
                const key = FIELD_ALIASES[error.field];
                const text = errorText(error.code);
                return (
                  <li key={index}>
                    {key ? (
                      <a
                        href={`#${id(key === 'guardian' ? 'guardian.name' : key)}`}
                        onClick={focusField(key)}
                      >
                        <span>{FIELD_LABELS[key]}: </span>
                        {text}
                      </a>
                    ) : (
                      text
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        </div>
      ) : null}

      <fieldset className="student-form__section">
        <legend className="student-form__legend">Aluno</legend>
        {textField({ key: 'name', value: values.name, onChange: (v) => set('name', v), maxLength: 120 })}
        {textField({
          key: 'birthDate',
          value: values.birthDate,
          onChange: (v) => set('birthDate', v),
          type: 'date',
        })}
        {textField({
          key: 'cpf',
          value: values.cpf,
          onChange: (v) => set('cpf', v),
          maxLength: 14,
          hintId: mode === 'edit' ? cpfHintId : undefined,
          hint: mode === 'edit' ? msg('MY_STUDENTS_CPF_KEEP') : undefined,
          extra: { inputMode: 'numeric', autoCapitalize: 'off', spellCheck: false },
        })}
        {textField({
          key: 'instrument',
          value: values.instrument,
          onChange: (v) => set('instrument', v),
          maxLength: 60,
        })}
      </fieldset>

      <fieldset className="student-form__section" aria-describedby={guardianDescribedBy}>
        <legend className="student-form__legend">{msg('MY_STUDENTS_GUARDIAN_SECTION')}</legend>
        {guardianGroupErrors.length > 0 ? fieldErrorsFor('guardian') : null}
        {textField({
          key: 'guardian.name',
          value: values.guardianName,
          onChange: (v) => set('guardianName', v),
          maxLength: 120,
        })}
        {textField({
          key: 'guardian.phone',
          value: values.guardianPhone,
          onChange: (v) => set('guardianPhone', v),
          type: 'tel',
          maxLength: 20,
        })}
        {textField({
          key: 'guardian.email',
          value: values.guardianEmail,
          onChange: (v) => set('guardianEmail', v),
          type: 'email',
          maxLength: 254,
        })}
      </fieldset>

      {showConsent ? (
        <fieldset className="student-form__section" aria-describedby={errorIds('consent').join(' ') || undefined}>
          <legend className="student-form__legend">{msg('CONSENT_SECTION')}</legend>
          {fieldErrorsFor('consent')}
          {props.consentTerm ? (
            <div className="field__help" id={id('consent-term')}>
              {msg('CONSENT_TERM_CURRENT', {
                number: props.consentTerm.number,
                date: props.consentTerm.publishedOn.split('-').reverse().join('/'),
              })}{' '}
              <details>
                <summary>{msg('CONSENT_TERM_READ')}</summary>
                <p className="student-form__term">{props.consentTerm.text}</p>
              </details>
              {props.consentTerm.privacyContact.contact ? (
                <span>
                  {msg('CONSENT_PRIVACY_CONTACT', {
                    name: props.consentTerm.privacyContact.name,
                    contact: props.consentTerm.privacyContact.contact,
                  })}
                </span>
              ) : null}
            </div>
          ) : (
            <div className="field__help">{msg('CONSENT_TERM_MISSING')}</div>
          )}
          {fieldErrorsFor('consent.termVersion')}
          {textField({
            key: 'consent.consentingName',
            value: values.consentingName,
            onChange: (v) => set('consentingName', v),
            maxLength: 120,
          })}
          <div className="field">
            <label htmlFor={id('consent.relationship')} className="field__label">
              {FIELD_LABELS['consent.relationship']}
            </label>
            <select
              id={id('consent.relationship')}
              className="input"
              value={values.consentRelationship}
              onChange={(event) => set('consentRelationship', event.target.value)}
              aria-invalid={(errorsByKey.get('consent.relationship') ?? []).length > 0 ? true : undefined}
              aria-describedby={errorIds('consent.relationship').join(' ') || undefined}
            >
              <option value="">Selecione</option>
              <option value="MOTHER">Mãe</option>
              <option value="FATHER">Pai</option>
              <option value="LEGAL_GUARDIAN">Responsável legal</option>
              <option value="OTHER">Outra relação</option>
            </select>
            {fieldErrorsFor('consent.relationship')}
          </div>
          {textField({
            key: 'consent.consentedOn',
            value: values.consentedOn,
            onChange: (v) => set('consentedOn', v),
            type: 'date',
          })}
          <div className="field">
            <label htmlFor={id('consent.collectionMethod')} className="field__label">
              {FIELD_LABELS['consent.collectionMethod']}
            </label>
            <select
              id={id('consent.collectionMethod')}
              className="input"
              value={values.consentMethod}
              onChange={(event) => set('consentMethod', event.target.value)}
              aria-invalid={(errorsByKey.get('consent.collectionMethod') ?? []).length > 0 ? true : undefined}
              aria-describedby={errorIds('consent.collectionMethod').join(' ') || undefined}
            >
              <option value="">Selecione</option>
              <option value="SIGNED_PAPER">Papel assinado</option>
              <option value="DIGITAL">Digital</option>
              <option value="OTHER">Outra forma</option>
            </select>
            {fieldErrorsFor('consent.collectionMethod')}
          </div>
        </fieldset>
      ) : null}

      {variant === 'admin' ? (
        <fieldset
          className="student-form__section"
          aria-describedby={errorIds('teacherIds').join(' ') || undefined}
        >
          <legend className="student-form__legend">Professores</legend>
          {(teacherOptions ?? []).length === 0 ? (
            <div className="field__help">Nenhum professor ativo cadastrado.</div>
          ) : (
            (teacherOptions ?? []).map((teacher, index) => (
              <label key={teacher.id} className="student-form__checkbox">
                <input
                  id={index === 0 ? id('teacherIds') : undefined}
                  type="checkbox"
                  checked={values.teacherIds.includes(teacher.id)}
                  onChange={(event) =>
                    set(
                      'teacherIds',
                      event.target.checked
                        ? [...values.teacherIds, teacher.id]
                        : values.teacherIds.filter((current) => current !== teacher.id),
                    )
                  }
                />
                <span>{teacher.name}</span>
              </label>
            ))
          )}
          {fieldErrorsFor('teacherIds')}
        </fieldset>
      ) : null}

      {blocked ? (
        <div role="status" aria-live="polite" className="alert alert--notice">
          <Icon name="lock_clock" />
          <div className="alert__text">
            {msg('OPERATION_TEMPORARILY_BLOCKED', { minutes: Math.max(1, Math.ceil(blockedSeconds / 60)) })}
          </div>
        </div>
      ) : null}

      <div className="student-form__actions">
        <Button type="submit" size="large" loading={submitting} disabled={blocked}>
          {mode === 'create' ? 'Cadastrar aluno' : 'Salvar alterações'}
        </Button>
        <Button variant="secondary" size="large" onClick={onCancel}>
          Cancelar
        </Button>
      </div>
    </form>
  );
}
