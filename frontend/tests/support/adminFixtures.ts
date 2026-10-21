import type { components } from '../../src/services/administration-api-types';

/*
 * Fixtures da área administrativa. Dados 100% fictícios, tipados pelo contrato gerado
 * (`administration-api-types.ts`): e-mails em `@example.test`, CPF de teste com dígitos verificadores
 * válidos e códigos que não existem em nenhum ambiente. Nunca use dado real aqui.
 */

type Schemas = components['schemas'];

export type AdministratorSession = Schemas['AdministratorSession'];
export type Teacher = Schemas['Teacher'];
export type TeacherWithCode = Schemas['TeacherWithCode'];
export type Administrator = Schemas['Administrator'];
export type AdministratorWithActivationCode = Schemas['AdministratorWithActivationCode'];
export type Student = Schemas['Student'];
export type AdministratorStudentInput = Schemas['AdministratorStudentInput'];
export type ContractError = Schemas['Error'];

/** CPF fictício de teste (dígitos verificadores válidos). Só aparece em corpos de entrada. */
export const TEST_CPF = '11144477735';
export const TEST_CPF_MASKED = '111.444.777-35';

/** Instante fixo para as sessões simuladas (login + 2 h). */
export const ADMIN_SESSION_EXPIRES_AT = '2026-09-21T14:00:00Z';

export const ADMIN_SESSION: AdministratorSession = {
  administrator: { id: '0190a000-0000-7000-8000-0000000000a1', name: 'Marina Duarte' },
  expiresAt: ADMIN_SESSION_EXPIRES_AT,
  mustChangePassword: false,
};

/** Sessão aberta com senha provisória: o servidor só permite trocar a senha. */
export const ADMIN_SESSION_PASSWORD_CHANGE_REQUIRED: AdministratorSession = {
  ...ADMIN_SESSION,
  mustChangePassword: true,
};

export const TEACHER_ACTIVE: Teacher = {
  id: '0190a000-0000-7000-8000-0000000000b1',
  name: 'Clara Souza',
  email: 'clara@example.test',
  active: true,
  telegramLinked: true,
};

export const TEACHER_INACTIVE: Teacher = {
  id: '0190a000-0000-7000-8000-0000000000b2',
  name: 'Bruno Lima',
  email: 'bruno@example.test',
  active: false,
  telegramLinked: false,
};

/** Código de acesso fictício (8 caracteres), exibido uma única vez. */
export const TEACHER_ACCESS_CODE = 'TSTC0D3X';

export const TEACHER_WITH_CODE: TeacherWithCode = {
  teacher: TEACHER_ACTIVE,
  accessCode: TEACHER_ACCESS_CODE,
  validUntil: '2026-09-28T12:00:00Z',
};

export const ADMINISTRATOR_ACTIVE: Administrator = {
  id: '0190a000-0000-7000-8000-0000000000c1',
  name: 'Marina Duarte',
  email: 'marina@example.test',
  active: true,
  activationState: 'ACTIVATED',
  activatedAt: '2026-09-01T12:00:00Z',
};

export const ADMINISTRATOR_PENDING: Administrator = {
  id: '0190a000-0000-7000-8000-0000000000c2',
  name: 'Renato Alves',
  email: 'renato@example.test',
  active: true,
  activationState: 'PENDING',
};

/** Código de ativação fictício (12 caracteres), exibido uma única vez. */
export const ADMINISTRATOR_ACTIVATION_CODE = 'TSTACT1V8D3X';

export const ADMINISTRATOR_WITH_ACTIVATION_CODE: AdministratorWithActivationCode = {
  administrator: ADMINISTRATOR_PENDING,
  activationCode: ADMINISTRATOR_ACTIVATION_CODE,
  validUntil: '2026-09-28T12:00:00Z',
};

/** Aluno devolvido pelas rotas `/admin/**` (com os professores associados; nunca com CPF). */
export function administratorStudent(overrides: Partial<Student> = {}): Student {
  return {
    id: '0190a000-0000-7000-8000-0000000000d1',
    name: 'Matteo Mafra',
    birthDate: '2012-05-10',
    age: 14,
    instrument: 'Piano',
    active: true,
    guardian: { name: 'Lívia Mafra', phone: '11987654321', email: 'livia@example.test' },
    teachers: [{ id: TEACHER_ACTIVE.id, name: TEACHER_ACTIVE.name, active: true }],
    ...overrides,
  };
}

/** Corpo de entrada do cadastro de aluno pelo administrador (único lugar com o CPF). */
export function administratorStudentInput(
  overrides: Partial<AdministratorStudentInput> = {},
): AdministratorStudentInput {
  return {
    name: 'Matteo Mafra',
    birthDate: '2012-05-10',
    cpf: TEST_CPF,
    instrument: 'Piano',
    guardian: { name: 'Lívia Mafra', phone: '(11) 98765-4321', email: 'livia@example.test' },
    teacherIds: [TEACHER_ACTIVE.id],
    ...overrides,
  };
}

/** Página no formato do contrato (`page` base 0). */
export function pageOf<T>(
  items: T[],
  options: { page?: number; size?: number; total?: number } = {},
): { items: T[]; page: number; size: number; total: number } {
  return {
    items,
    page: options.page ?? 0,
    size: options.size ?? 20,
    total: options.total ?? items.length,
  };
}

/** Resposta 400 `VALIDATION_FAILED` com os campos recusados (nunca repete o valor digitado). */
export function validationError(fields: { field: string; code: string }[]): ContractError {
  return {
    code: 'VALIDATION_FAILED',
    message: 'Confira os campos destacados.',
    details: { fields },
  };
}

/** Resposta 429 de bloqueio temporário, com o tempo restante. */
export function blockedError(retryAfterSeconds: number): ContractError {
  return {
    code: 'OPERATION_TEMPORARILY_BLOCKED',
    message: 'Muitas tentativas. Tente de novo em instantes.',
    retryAfterSeconds,
  };
}

/** Prévia da retirada de associação: relatórios em aberto que ficariam congelados. */
export const REMOVAL_PREVIEW: Schemas['RemovalPreview'] = { frozenReports: 2 };
