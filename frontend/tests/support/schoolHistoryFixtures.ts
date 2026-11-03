import type { components } from '../../src/services/school-history-api-types';

/*
 * Fixtures da visão da escola (feature 006), tipadas pelo contrato gerado (`school-history-api-types.ts`).
 * Dados 100% fictícios: nunca use dado real aqui.
 */

type Schemas = components['schemas'];

export type SchoolHistoryItem = Schemas['SchoolHistoryItem'];
export type SchoolHistoryPage = Schemas['SchoolHistoryPage'];
export type PersonSummary = Schemas['PersonSummary'];

export const HISTORY_STUDENT: PersonSummary = {
  id: '0190a000-0000-7000-8000-0000000000d1',
  name: 'Matteo Mafra',
  active: true,
};

export const HISTORY_TEACHER: PersonSummary = {
  id: '0190a000-0000-7000-8000-0000000000b1',
  name: 'Clara Souza',
  active: true,
};

export const HISTORY_TEACHER_INACTIVE: PersonSummary = {
  id: '0190a000-0000-7000-8000-0000000000b2',
  name: 'Bruno Lima',
  active: false,
};

/** Item de aula aprovado (v3); o professor e o aluno podem ser trocados. */
export function schoolHistoryItem(overrides: Partial<SchoolHistoryItem> = {}): SchoolHistoryItem {
  return {
    type: 'LESSON',
    reportId: '0190a000-0000-7000-8000-0000000000e1',
    label: 'Aula de 10/03/2026',
    lessonDate: '2026-03-10',
    student: HISTORY_STUDENT,
    teacher: HISTORY_TEACHER,
    approvedAt: '2026-03-10T21:30:00Z',
    approvedVersion: 3,
    ...overrides,
  };
}

/** Página no formato do contrato, com o resumo do aluno quando houver filtro de aluno. */
export function schoolHistoryPage(
  items: SchoolHistoryItem[],
  options: { student?: PersonSummary; page?: number; size?: number; total?: number } = {},
): SchoolHistoryPage {
  return {
    ...(options.student ? { student: options.student } : {}),
    items,
    page: options.page ?? 0,
    size: options.size ?? 20,
    total: options.total ?? items.length,
  };
}
