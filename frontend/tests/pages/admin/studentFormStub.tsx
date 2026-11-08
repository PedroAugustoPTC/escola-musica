import type { StudentFormProps, StudentFormValues } from '../../../src/components/student/studentFormTypes';
import { TEACHER_ACTIVE, TEST_CPF_MASKED } from '../../support/adminFixtures';

/*
 * Dublê mínimo do `StudentForm` compartilhado (a versão real é do fluxo "Meus alunos" e tem teste próprio).
 * Os testes das telas administrativas conferem só a fiação: propriedades recebidas e valores enviados.
 */

export const stub: { values: Partial<StudentFormValues> } = { values: {} };

export function resetStub(): void {
  stub.values = {};
}

function defaultValues(props: StudentFormProps): StudentFormValues {
  const student = props.student;
  return {
    name: student?.name ?? 'Matteo Mafra',
    birthDate: student?.birthDate ?? '2012-05-10',
    // na edição o CPF vem em branco (em branco mantém o atual)
    cpf: props.mode === 'edit' ? '' : TEST_CPF_MASKED,
    instrument: student?.instrument ?? 'Piano',
    guardianName: student?.guardian?.name ?? 'Lívia Mafra',
    guardianPhone: student?.guardian?.phone ?? '(11) 98765-4321',
    guardianEmail: student?.guardian?.email ?? 'livia@example.test',
    teacherIds: props.mode === 'create' ? [TEACHER_ACTIVE.id] : [],
    consentingName: '',
    consentRelationship: '',
    consentedOn: '',
    consentMethod: '',
    consentTermVersion: null,
    ...stub.values,
  };
}

export function StudentForm(props: StudentFormProps) {
  return (
    <form
      aria-label="Formulário de aluno"
      onSubmit={(event) => {
        event.preventDefault();
        props.onSubmit(defaultValues(props));
      }}
    >
      <p>
        modo {props.variant}/{props.mode}
      </p>
      <ul aria-label="Professores oferecidos">
        {(props.teacherOptions ?? []).map((teacher) => (
          <li key={teacher.id}>{teacher.name}</li>
        ))}
      </ul>
      <ul aria-label="Erros por campo">
        {props.fieldErrors.map((error) => (
          <li key={`${error.field}:${error.code}`}>{`${error.field}:${error.code}`}</li>
        ))}
      </ul>
      <button type="submit" disabled={props.submitting}>
        Salvar aluno
      </button>
      <button type="button" onClick={props.onCancel}>
        Cancelar formulário
      </button>
    </form>
  );
}
