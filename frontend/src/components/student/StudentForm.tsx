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
  | 'teacherIds';

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
};

/** Texto do catálogo para o código do erro; código desconhecido cai na mensagem geral de validação. */
function errorText(code: string): string {
  return hasMessage(code) ? msg(code) : msg('VALIDATION_FAILED');
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
  };
}

/**
 * Formulário de aluno compartilhado por A11/A12 (administrador) e P02/P03 (professor), conforme
 * `contracts/portal-screens.md` §3 e §4. Só valida a forma (tamanho máximo); as regras de negócio
 * (CPF, data, responsável de menor, unicidade) vêm do servidor em `details.fields`.
 */
export function StudentForm(props: StudentFormProps) {
  const { variant, mode, teacherOptions, fieldErrors, submitting, blockedSeconds, onSubmit, onCancel } = props;
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
    onSubmit(values);
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

  const textField = ({ key, value, onChange, type = 'text', maxLength, hintId, hint, extra }: TextFieldOptions) => {
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
                      <a href={`#${id(key === 'guardian' ? 'guardian.name' : key)}`} onClick={focusField(key)}>
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

      {variant === 'admin' ? (
        <fieldset className="student-form__section" aria-describedby={errorIds('teacherIds').join(' ') || undefined}>
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
