import type { components } from '../../services/administration-api-types';

/** Aluno devolvido pela API (nunca contém CPF). */
export type StudentView = components['schemas']['Student'];

/** Quem usa o formulário: o administrador (com a seção de professores) ou o professor (sem ela). */
export type StudentFormVariant = 'admin' | 'teacher';

/** Cadastro novo ou edição; na edição o CPF começa em branco (em branco mantém o atual). */
export type StudentFormMode = 'create' | 'edit';

/** Valores digitados; todos texto, sem máscara que vaze para URL. A validade é decidida no backend. */
export interface StudentFormValues {
  name: string;
  birthDate: string;
  cpf: string;
  instrument: string;
  guardianName: string;
  guardianPhone: string;
  guardianEmail: string;
  /** Usado só na variante do administrador. */
  teacherIds: string[];
}

/** Erro de um campo, vindo de `details.fields` da resposta de validação. */
export interface StudentFormFieldError {
  field: string;
  code: string;
}

/** Professor ativo oferecido na multisseleção (variante do administrador). */
export interface StudentFormTeacherOption {
  id: string;
  name: string;
}

/** Propriedades do formulário de aluno compartilhado por A11/A12 e P02/P03 (contracts/portal-screens.md §3 e §4). */
export interface StudentFormProps {
  variant: StudentFormVariant;
  mode: StudentFormMode;
  /** Aluno carregado na edição; ausente no cadastro. */
  student?: StudentView;
  /** Professores ativos para a multisseleção; só na variante do administrador. */
  teacherOptions?: StudentFormTeacherOption[];
  fieldErrors: StudentFormFieldError[];
  submitting: boolean;
  /** Tempo restante do bloqueio contra sondagem de CPF; desabilita só o envio. `null` sem bloqueio. */
  blockedSeconds: number | null;
  onSubmit: (values: StudentFormValues) => void;
  onCancel: () => void;
}
