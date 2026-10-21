import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { useQuery } from '@tanstack/react-query';
import { request } from '../../src/services/apiClient';
import { renderAsAdministrator, renderAsTeacher } from '../utils';
import {
  ADMIN_SESSION,
  ADMINISTRATOR_ACTIVE,
  ADMINISTRATOR_PENDING,
  TEACHER_ACTIVE,
  TEACHER_INACTIVE,
  TEST_CPF,
  TEST_CPF_MASKED,
  administratorStudentInput,
  administratorStudent,
  blockedError,
  pageOf,
  validationError,
} from './adminFixtures';
import {
  MY_STUDENT_ACTIVE,
  MY_STUDENT_SHARED,
  myStudentInput,
  myStudentsPage,
} from './myStudentsFixtures';

/** Soma dos dígitos do CPF com os pesos dados (algoritmo público do dígito verificador). */
function checkDigit(digits: number[], startWeight: number): number {
  const sum = digits.reduce((total, digit, index) => total + digit * (startWeight - index), 0);
  const remainder = (sum * 10) % 11;
  return remainder === 10 ? 0 : remainder;
}

function SessionName({ path }: { path: string }) {
  const query = useQuery({
    queryKey: ['support-sanity', path],
    queryFn: () => request<{ administrator?: { name: string }; teacher?: { name: string } }>(path),
  });
  const data = query.data;
  if (!data) return <p>carregando</p>;
  return <p>{data.administrator?.name ?? data.teacher?.name}</p>;
}

describe('fixtures de teste', () => {
  it('usa um CPF fictício com os dígitos verificadores corretos', () => {
    const digits = TEST_CPF.split('').map(Number);
    expect(digits).toHaveLength(11);
    expect(checkDigit(digits.slice(0, 9), 10)).toBe(digits[9]);
    expect(checkDigit(digits.slice(0, 10), 11)).toBe(digits[10]);
    expect(TEST_CPF_MASKED.replace(/\D/g, '')).toBe(TEST_CPF);
  });

  it('usa só e-mails de domínio reservado para teste', () => {
    const emails = [
      TEACHER_ACTIVE.email,
      TEACHER_INACTIVE.email,
      ADMINISTRATOR_ACTIVE.email,
      ADMINISTRATOR_PENDING.email,
      administratorStudentInput().guardian?.email,
      myStudentInput().guardian?.email,
    ];
    for (const email of emails) expect(email).toMatch(/@example\.test$/);
  });

  it('nunca põe o CPF numa resposta de aluno nem de lista', () => {
    const responses = [
      administratorStudent(),
      myStudentsPage([MY_STUDENT_ACTIVE, MY_STUDENT_SHARED]),
      pageOf([TEACHER_ACTIVE, TEACHER_INACTIVE]),
    ];
    for (const response of responses) {
      expect(JSON.stringify(response)).not.toContain(TEST_CPF);
      expect(JSON.stringify(response)).not.toContain(TEST_CPF_MASKED);
    }
  });

  it('só o corpo de entrada carrega o CPF', () => {
    expect(administratorStudentInput().cpf).toBe(TEST_CPF);
    expect(myStudentInput().cpf).toBe(TEST_CPF);
  });

  it('o aluno compartilhado não pode ser inativado pelo professor', () => {
    expect(MY_STUDENT_SHARED.canInactivate).toBe(false);
    expect(MY_STUDENT_ACTIVE.canInactivate).toBe(true);
  });

  it('monta páginas, erro de validação e bloqueio no formato do contrato', () => {
    expect(pageOf([TEACHER_ACTIVE], { page: 2, size: 5, total: 11 })).toEqual({
      items: [TEACHER_ACTIVE],
      page: 2,
      size: 5,
      total: 11,
    });
    const invalid = validationError([{ field: 'birthDate', code: 'INVALID_BIRTH_DATE' }]);
    expect(invalid.code).toBe('VALIDATION_FAILED');
    expect(invalid.details?.fields).toEqual([{ field: 'birthDate', code: 'INVALID_BIRTH_DATE' }]);
    const blocked = blockedError(42);
    expect(blocked.code).toBe('OPERATION_TEMPORARILY_BLOCKED');
    expect(blocked.retryAfterSeconds).toBe(42);
  });

  it('a sessão do administrador expira e não exige troca de senha por padrão', () => {
    expect(ADMIN_SESSION.mustChangePassword).toBe(false);
    expect(new Date(ADMIN_SESSION.expiresAt).getTime()).not.toBeNaN();
  });
});

describe('renderAsAdministrator', () => {
  it('renderiza a rota com a sessão do administrador simulada e cache sem retenção', async () => {
    const { client, calls } = renderAsAdministrator(
      [{ path: '/admin', element: <SessionName path="/admin/session" /> }],
      '/admin',
    );
    expect(await screen.findByText(ADMIN_SESSION.administrator.name)).toBeInTheDocument();
    expect(client.getDefaultOptions().queries?.gcTime).toBe(0);
    expect(calls.some((c) => c.method === 'GET' && c.url.endsWith('/admin/session'))).toBe(true);
  });

  it('deixa as rotas da API ser substituídas e devolve o roteador', async () => {
    const { router } = renderAsAdministrator(
      [{ path: '/admin', element: <SessionName path="/admin/session" /> }],
      '/admin',
      {
        'GET /api/v1/admin/session': {
          body: { ...ADMIN_SESSION, administrator: { ...ADMIN_SESSION.administrator, name: 'Outra Pessoa' } },
        },
      },
    );
    expect(await screen.findByText('Outra Pessoa')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin');
  });
});

describe('renderAsTeacher', () => {
  it('renderiza a rota com a sessão do professor simulada e cache sem retenção', async () => {
    const { client, calls } = renderAsTeacher(
      [{ path: '/my-students', element: <SessionName path="/session" /> }],
      '/my-students',
    );
    expect(await screen.findByText('Clara Souza')).toBeInTheDocument();
    expect(client.getDefaultOptions().queries?.gcTime).toBe(0);
    expect(calls.some((c) => c.method === 'GET' && c.url.endsWith('/session'))).toBe(true);
  });
});
