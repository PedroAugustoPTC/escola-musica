import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  TEACHER_ACTIVE,
  TEST_CPF,
  TEST_CPF_MASKED,
  administratorStudent,
  pageOf,
  validationError,
} from '../../support/adminFixtures';
import { renderAdminArea } from './adminTestSupport';
import { resetStub, stub } from './studentFormStub';

vi.mock('../../../src/components/student/StudentForm', async () => await import('./studentFormStub'));

const TEACHERS = 'GET /api/v1/admin/teachers';
const CREATE = 'POST /api/v1/admin/students';
const CREATED = administratorStudent();

function open(apiRoutes: Parameters<typeof renderAdminArea>[1] = {}) {
  return renderAdminArea('/admin/students/new', {
    [TEACHERS]: { body: pageOf([TEACHER_ACTIVE]) },
    ...apiRoutes,
  });
}

describe('StudentFormScreen (A11)', () => {
  beforeEach(() => resetStub());

  it('oferece só professores ativos na multisseleção', async () => {
    const api = open();
    expect(await screen.findByRole('form', { name: 'Formulário de aluno' })).toBeInTheDocument();
    expect(screen.getByText('modo admin/create')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Professores oferecidos' })).toHaveTextContent('Clara Souza');
    const request = api.calls.find((call) => call.url.startsWith('/api/v1/admin/teachers'))!;
    expect(request.url).toContain('active=true');
    expect(request.url).toContain('size=100');
  });

  it('cadastra o aluno com os professores e leva ao aluno, sem o CPF na URL nem nos caches', async () => {
    const api = open({
      [CREATE]: { status: 201, body: CREATED },
      [`GET /api/v1/admin/students/${CREATED.id}`]: { body: CREATED },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salvar aluno' }));

    await waitFor(() => expect(api.router.state.location.pathname).toBe(`/admin/students/${CREATED.id}`));
    const post = api.calls.find((call) => call.method === 'POST')!;
    expect(JSON.parse(post.init.body as string)).toEqual({
      name: 'Matteo Mafra',
      birthDate: '2012-05-10',
      cpf: TEST_CPF_MASKED,
      instrument: 'Piano',
      guardian: { name: 'Lívia Mafra', phone: '(11) 98765-4321', email: 'livia@example.test' },
      teacherIds: [TEACHER_ACTIVE.id],
    });
    expect(api.router.state.location.search).toBe('');
    expect(api.router.state.location.pathname).not.toContain(TEST_CPF);
    const caches = JSON.stringify([
      api.client
        .getQueryCache()
        .getAll()
        .map((query) => [query.queryKey, query.state.data ?? null]),
      api.client
        .getMutationCache()
        .getAll()
        .map((mutation) => [mutation.state.variables ?? null]),
    ]);
    expect(caches).not.toContain(TEST_CPF);
    expect(caches).not.toContain('111.444');
  });

  it('adulto sem responsável e sem instrumento não envia esses campos', async () => {
    stub.values = {
      guardianName: '',
      guardianPhone: '',
      guardianEmail: '',
      instrument: '',
      teacherIds: [],
    };
    const api = open({
      [CREATE]: { status: 201, body: CREATED },
      [`GET /api/v1/admin/students/${CREATED.id}`]: { body: CREATED },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salvar aluno' }));
    await waitFor(() => expect(api.router.state.location.pathname).toBe(`/admin/students/${CREATED.id}`));
    const post = api.calls.find((call) => call.method === 'POST')!;
    expect(JSON.parse(post.init.body as string)).toEqual({
      name: 'Matteo Mafra',
      birthDate: '2012-05-10',
      cpf: TEST_CPF_MASKED,
      teacherIds: [],
    });
  });

  it('erros por campo da validação chegam ao formulário', async () => {
    open({
      [CREATE]: {
        status: 400,
        body: validationError([
          { field: 'cpf', code: 'INVALID_CPF' },
          { field: 'birthDate', code: 'INVALID_BIRTH_DATE' },
        ]),
      },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salvar aluno' }));
    const errors = await screen.findByRole('list', { name: 'Erros por campo' });
    await waitFor(() => expect(errors).toHaveTextContent('cpf:INVALID_CPF'));
    expect(errors).toHaveTextContent('birthDate:INVALID_BIRTH_DATE');
    expect(screen.getByText('modo admin/create')).toBeInTheDocument();
  });

  it('CPF repetido mostra o nome do aluno existente, nunca o CPF', async () => {
    open({
      [CREATE]: {
        status: 409,
        body: { code: 'DUPLICATE_CPF', message: 'x', details: { name: 'Matteo Mafra' } },
      },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salvar aluno' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Este CPF já pertence ao aluno Matteo Mafra.');
    expect(document.body.textContent).not.toContain('111.444');
    expect(document.body.textContent).not.toContain(TEST_CPF);
  });

  it('professor inativo e falha geral mostram o texto do catálogo e não avançam a tela', async () => {
    const api = open({
      [CREATE]: { status: 409, body: { code: 'TEACHER_INACTIVE', message: 'x' } },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salvar aluno' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Este professor está inativo. Reative-o antes.',
    );
    expect(api.router.state.location.pathname).toBe('/admin/students/new');
  });

  it('falha ao carregar os professores mostra o erro com nova tentativa', async () => {
    open({ [TEACHERS]: { status: 500, body: { code: 'INTERNAL_ERROR', message: 'Falhou.' } } });
    expect(await screen.findByRole('alert')).toHaveTextContent('Falhou.');
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Formulário de aluno' })).not.toBeInTheDocument();
  });

  it('"Cancelar" volta à lista', async () => {
    const api = open({ 'GET /api/v1/admin/students': { body: pageOf([]) } });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Cancelar formulário' }));
    await waitFor(() => expect(api.router.state.location.pathname).toBe('/admin/students'));
  });
});
