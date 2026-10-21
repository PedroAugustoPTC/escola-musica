import type { components } from '../../src/services/administration-api-types';
import { TEST_CPF } from './adminFixtures';

/*
 * Fixtures de "Meus alunos" (professor). Dados 100% fictícios, tipados pelo contrato gerado.
 * Nas rotas `/my-students` o aluno nunca traz `teachers` (não revela outros professores) e traz
 * `canInactivate` (falso quando há mais de um professor ativo).
 */

type Schemas = components['schemas'];

export type MyStudent = Schemas['Student'];
export type MyStudentInput = Schemas['StudentInput'];

export const MY_STUDENT_ACTIVE: MyStudent = {
  id: '0190a000-0000-7000-8000-0000000000e1',
  name: 'Olavo Torrente',
  birthDate: '2010-03-22',
  age: 16,
  instrument: 'Violão',
  active: true,
  guardian: { name: 'Sônia Torrente', phone: '11912345678', email: 'sonia@example.test' },
  canInactivate: true,
};

/** Aluno com mais de um professor ativo: o professor não pode inativá-lo sozinho. */
export const MY_STUDENT_SHARED: MyStudent = {
  id: '0190a000-0000-7000-8000-0000000000e2',
  name: 'Miguel de Castro',
  birthDate: '2008-11-03',
  age: 17,
  instrument: 'Violino',
  active: true,
  canInactivate: false,
};

export const MY_STUDENT_INACTIVE: MyStudent = {
  id: '0190a000-0000-7000-8000-0000000000e3',
  name: 'Helena Prado',
  birthDate: '2001-07-15',
  age: 25,
  instrument: 'Flauta',
  active: false,
  canInactivate: true,
};

/** Página de `GET /my-students` (`page` base 0). */
export function myStudentsPage(
  items: MyStudent[],
  options: { page?: number; size?: number; total?: number } = {},
): Schemas['StudentPage'] {
  return {
    items,
    page: options.page ?? 0,
    size: options.size ?? 20,
    total: options.total ?? items.length,
  };
}

/** Corpo de entrada do cadastro pelo professor (único lugar com o CPF). */
export function myStudentInput(overrides: Partial<MyStudentInput> = {}): MyStudentInput {
  return {
    name: 'Olavo Torrente',
    birthDate: '2010-03-22',
    cpf: TEST_CPF,
    instrument: 'Violão',
    guardian: { name: 'Sônia Torrente', phone: '(11) 91234-5678', email: 'sonia@example.test' },
    ...overrides,
  };
}
