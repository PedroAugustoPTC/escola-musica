import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../src/app/routes';
import { msg } from '../../src/messages/messages';
import { renderAsTeacher, type SimulatedResponse, type SimulatedRoute } from '../utils';
import { TEST_CPF, TEST_CPF_MASKED, blockedError, validationError } from '../support/adminFixtures';
import { MY_STUDENT_ACTIVE, myStudentInput, myStudentsPage } from '../support/myStudentsFixtures';

type Api = Record<string, SimulatedResponse | SimulatedRoute>;

const LIST: Api = { 'GET /api/v1/my-students': { body: myStudentsPage([]) } };

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Nome do aluno'), 'Olavo Torrente');
  await user.type(screen.getByLabelText('Data de nascimento'), '2010-03-22');
  await user.type(screen.getByLabelText('CPF'), TEST_CPF_MASKED);
  await user.type(screen.getByLabelText('Instrumento'), 'Violão');
  await user.type(screen.getByLabelText('Nome do responsável'), 'Sônia Torrente');
  await user.type(screen.getByLabelText('Telefone do responsável'), '(11) 91234-5678');
  await user.type(screen.getByLabelText('E-mail do responsável'), 'sonia@example.test');
}

function sentBody(calls: { method: string; url: string; init: RequestInit }[]) {
  const post = calls.find((c) => c.method === 'POST' && c.url === '/api/v1/my-students')!;
  return JSON.parse(post.init.body as string) as Record<string, unknown>;
}

describe('P02 Cadastrar aluno', () => {
  it('mostra o formulário do professor: Responsável sempre visível e nenhuma seção de professores', async () => {
    renderAsTeacher(routes, '/my-students/new', LIST);
    expect(await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: msg('MY_STUDENTS_GUARDIAN_SECTION') })).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Professores' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('CPF')).toHaveValue('');
  });

  it('cadastra o aluno, envia o CPF só no corpo e leva ao detalhe com o aviso de sucesso', async () => {
    const user = userEvent.setup();
    const created = { ...MY_STUDENT_ACTIVE };
    const api = renderAsTeacher(routes, '/my-students/new', {
      ...LIST,
      'POST /api/v1/my-students': { status: 201, body: created },
      [`GET /api/v1/my-students/${created.id}`]: { body: created },
    });
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));

    await waitFor(() => expect(api.router.state.location.pathname).toBe(`/my-students/${created.id}`));
    const body = sentBody(api.calls);
    expect(body).toMatchObject({
      name: 'Olavo Torrente',
      birthDate: '2010-03-22',
      cpf: TEST_CPF_MASKED,
      instrument: 'Violão',
      guardian: { name: 'Sônia Torrente', phone: '(11) 91234-5678', email: 'sonia@example.test' },
    });
    const post = api.calls.find((c) => c.method === 'POST')!;
    expect((post.init.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('test-token');
    expect(await screen.findByText(msg('REGISTRY_SAVED', { name: 'Olavo Torrente' }))).toBeInTheDocument();
  });

  it('omite o instrumento e o responsável quando não foram preenchidos', async () => {
    const user = userEvent.setup();
    const api = renderAsTeacher(routes, '/my-students/new', {
      ...LIST,
      'POST /api/v1/my-students': { status: 201, body: MY_STUDENT_ACTIVE },
      [`GET /api/v1/my-students/${MY_STUDENT_ACTIVE.id}`]: { body: MY_STUDENT_ACTIVE },
    });
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await user.type(screen.getByLabelText('Nome do aluno'), 'Helena Prado');
    await user.type(screen.getByLabelText('Data de nascimento'), '2001-07-15');
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));
    await waitFor(() => expect(api.calls.some((c) => c.method === 'POST')).toBe(true));
    const body = sentBody(api.calls);
    expect(body.instrument).toBeUndefined();
    expect(body.guardian).toBeUndefined();
    expect(body.cpf).toBe(TEST_CPF);
  });

  it('erros de campo do servidor aparecem no campo, no resumo com foco, e a tela não avança', async () => {
    const user = userEvent.setup();
    const { router } = renderAsTeacher(routes, '/my-students/new', {
      ...LIST,
      'POST /api/v1/my-students': {
        status: 400,
        body: validationError([
          { field: 'cpf', code: 'INVALID_CPF' },
          { field: 'guardian.name', code: 'GUARDIAN_REQUIRED' },
        ]),
      },
    });
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await user.type(screen.getByLabelText('Nome do aluno'), 'Olavo Torrente');
    await user.type(screen.getByLabelText('CPF'), '123');
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));

    const summary = await screen.findByRole('alert');
    expect(summary).toHaveFocus();
    expect(within(summary).getByText(msg('INVALID_CPF'))).toBeInTheDocument();
    expect(screen.getByLabelText('CPF')).toHaveAccessibleDescription(msg('INVALID_CPF'));
    expect(screen.getByLabelText('Nome do responsável')).toHaveAccessibleDescription(
      msg('GUARDIAN_REQUIRED'),
    );
    expect(router.state.location.pathname).toBe('/my-students/new');
    // o que foi digitado continua nos campos
    expect(screen.getByLabelText('Nome do aluno')).toHaveValue('Olavo Torrente');
  });

  it('CPF de aluno alheio mostra só CPF_UNAVAILABLE no campo, sem nome nem professor', async () => {
    const user = userEvent.setup();
    renderAsTeacher(routes, '/my-students/new', {
      ...LIST,
      'POST /api/v1/my-students': {
        status: 409,
        body: {
          code: 'CPF_UNAVAILABLE',
          message: 'Não foi possível usar este CPF. Procure o administrador.',
          details: { name: 'Fulano Alheio', teachers: ['Professor Alheio'] },
        },
      },
    });
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));

    await waitFor(() =>
      expect(screen.getByLabelText('CPF')).toHaveAccessibleDescription(msg('CPF_UNAVAILABLE')),
    );
    expect(screen.getByLabelText('CPF')).toHaveAttribute('aria-invalid', 'true');
    expect(document.body.textContent).not.toContain('Fulano Alheio');
    expect(document.body.textContent).not.toContain('Professor Alheio');
    expect(document.body.textContent).not.toContain(TEST_CPF);
  });

  it('CPF de aluno do próprio professor mostra STUDENT_ALREADY_YOURS no campo', async () => {
    const user = userEvent.setup();
    renderAsTeacher(routes, '/my-students/new', {
      ...LIST,
      'POST /api/v1/my-students': {
        status: 409,
        body: { code: 'STUDENT_ALREADY_YOURS', message: 'Este aluno já está entre os seus.' },
      },
    });
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));
    await waitFor(() =>
      expect(screen.getByLabelText('CPF')).toHaveAccessibleDescription(msg('STUDENT_ALREADY_YOURS')),
    );
  });

  it('bloqueio contra sondagem desabilita só o envio e mostra o tempo restante', async () => {
    const user = userEvent.setup();
    renderAsTeacher(routes, '/my-students/new', {
      ...LIST,
      'POST /api/v1/my-students': { status: 429, headers: { 'Retry-After': '120' }, body: blockedError(120) },
    });
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));

    const notice = await screen.findByText(msg('OPERATION_TEMPORARILY_BLOCKED', { minutes: 2 }));
    expect(notice.closest('[role="status"]')).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByRole('button', { name: 'Cadastrar aluno' })).toBeDisabled();
    // o resto do portal segue utilizável
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeEnabled();
    expect(screen.getByLabelText('Nome do aluno')).toBeEnabled();
    expect(screen.getByLabelText('CPF')).toHaveValue(TEST_CPF);
    expect(screen.getByRole('button', { name: 'Menu' })).toBeEnabled();
  });

  it('o CPF não aparece na URL, no armazenamento nem nas chaves do cache de consulta', async () => {
    const user = userEvent.setup();
    const { router, client } = renderAsTeacher(routes, '/my-students/new', {
      ...LIST,
      'POST /api/v1/my-students': { status: 201, body: MY_STUDENT_ACTIVE },
      [`GET /api/v1/my-students/${MY_STUDENT_ACTIVE.id}`]: { body: MY_STUDENT_ACTIVE },
    });
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF_MASKED);
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(`/my-students/${MY_STUDENT_ACTIVE.id}`));

    expect(router.state.location.search).toBe('');
    expect(JSON.stringify(router.state.location)).not.toContain(TEST_CPF);
    expect(
      JSON.stringify(
        client
          .getQueryCache()
          .getAll()
          .map((q) => q.queryKey),
      ),
    ).not.toContain(TEST_CPF);
    expect(JSON.stringify({ ...window.localStorage, ...window.sessionStorage })).not.toContain(TEST_CPF);
    expect(document.title).not.toContain(TEST_CPF);
  });

  it('Cancelar volta para a lista sem gravar', async () => {
    const user = userEvent.setup();
    const api = renderAsTeacher(routes, '/my-students/new', LIST);
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(api.router.state.location.pathname).toBe('/my-students'));
    expect(api.calls.some((c) => c.method === 'POST')).toBe(false);
  });

  it('falha de rede mantém a tela e mostra o erro', async () => {
    const user = userEvent.setup();
    const { router } = renderAsTeacher(routes, '/my-students/new', {
      ...LIST,
      'POST /api/v1/my-students': () => {
        throw new TypeError('Failed to fetch');
      },
    });
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await user.type(screen.getByLabelText('Nome do aluno'), 'Olavo Torrente');
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(msg('NO_CONNECTION'));
    expect(router.state.location.pathname).toBe('/my-students/new');
    expect(screen.getByRole('button', { name: 'Cadastrar aluno' })).toBeEnabled();
  });

  it('o payload de entrada do contrato usa os mesmos campos que o formulário', () => {
    expect(Object.keys(myStudentInput()).sort()).toEqual([
      'birthDate',
      'cpf',
      'guardian',
      'instrument',
      'name',
    ]);
  });

  it('DUPLICATE_CPF no fluxo do professor vira CPF_UNAVAILABLE: nunca "pertence ao aluno", sem oráculo de existência', async () => {
    const user = userEvent.setup();
    renderAsTeacher(routes, '/my-students/new', {
      ...LIST,
      'POST /api/v1/my-students': {
        status: 409,
        body: {
          code: 'DUPLICATE_CPF',
          message: 'Este CPF já pertence ao aluno Fulano Alheio.',
          details: { name: 'Fulano Alheio' },
        },
      },
    });
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));

    await waitFor(() =>
      expect(screen.getByLabelText('CPF')).toHaveAccessibleDescription(msg('CPF_UNAVAILABLE')),
    );
    expect(document.body.textContent).not.toContain('pertence ao aluno');
    expect(document.body.textContent).not.toContain('Fulano Alheio');
  });

  it('erro geral mostra o texto do catálogo pelo código, nunca a mensagem crua do servidor', async () => {
    const user = userEvent.setup();
    renderAsTeacher(routes, '/my-students/new', {
      ...LIST,
      'POST /api/v1/my-students': {
        status: 500,
        body: { code: 'RESOURCE_NOT_FOUND', message: 'java.lang.IllegalStateException: detalhe interno' },
      },
    });
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(msg('RESOURCE_NOT_FOUND'));
    expect(document.body.textContent).not.toContain('IllegalStateException');
  });

  it('código fora do catálogo cai em INTERNAL_ERROR', async () => {
    const user = userEvent.setup();
    renderAsTeacher(routes, '/my-students/new', {
      ...LIST,
      'POST /api/v1/my-students': {
        status: 500,
        body: { code: 'SOME_NEW_CODE', message: 'texto cru do servidor' },
      },
    });
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(msg('INTERNAL_ERROR'));
    expect(document.body.textContent).not.toContain('texto cru do servidor');
  });

  it('depois da resposta com erro a mutação é zerada: o CPF não fica nas variáveis em memória', async () => {
    const user = userEvent.setup();
    const api = renderAsTeacher(routes, '/my-students/new', {
      ...LIST,
      'POST /api/v1/my-students': {
        status: 400,
        body: validationError([{ field: 'cpf', code: 'INVALID_CPF' }]),
      },
    });
    await screen.findByRole('heading', { level: 1, name: 'Cadastrar aluno' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));

    // o erro de campo continua na tela, mesmo com a mutação zerada
    await waitFor(() => expect(screen.getByLabelText('CPF')).toHaveAccessibleDescription(msg('INVALID_CPF')));
    expect(
      JSON.stringify(
        api.client
          .getMutationCache()
          .getAll()
          .map((m) => m.state.variables ?? null),
      ),
    ).not.toContain(TEST_CPF);
  });
});
