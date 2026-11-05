import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../src/app/routes';
import { msg } from '../../src/messages/messages';
import { renderAsTeacher, type SimulatedResponse, type SimulatedRoute } from '../utils';
import { TEST_CPF, TEST_CPF_MASKED, blockedError, validationError } from '../support/adminFixtures';
import {
  MY_STUDENT_ACTIVE,
  MY_STUDENT_INACTIVE,
  MY_STUDENT_SHARED,
  myStudentsPage,
} from '../support/myStudentsFixtures';

type Api = Record<string, SimulatedResponse | SimulatedRoute>;

function detail(student: { id: string; active?: boolean }, extra: Api = {}): Api {
  return {
    [`GET /api/v1/my-students/${student.id}`]: { body: student },
    'GET /api/v1/my-students': { body: myStudentsPage([]) },
    ...extra,
  };
}

function bodyOf(call: { init: RequestInit }) {
  return JSON.parse(call.init.body as string) as Record<string, unknown>;
}

describe('P03 Aluno', () => {
  it('mostra o aluno com a situação e o formulário de edição com o CPF em branco', async () => {
    renderAsTeacher(routes, `/my-students/${MY_STUDENT_ACTIVE.id}`, detail(MY_STUDENT_ACTIVE));
    expect(await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' })).toBeInTheDocument();
    expect(screen.getByText('Ativo')).toBeInTheDocument();
    expect(screen.getByLabelText('Nome do aluno')).toHaveValue('Olavo Torrente');
    expect(screen.getByLabelText('CPF')).toHaveValue('');
    expect(screen.getByLabelText('CPF')).toHaveAccessibleDescription(msg('MY_STUDENTS_CPF_KEEP'));
    expect(screen.queryByRole('group', { name: 'Professores' })).not.toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/\d{3}\.?\d{3}\.?\d{3}-?\d{2}/);
  });

  it('aluno inexistente ou de outro professor mostra o erro neutro, sem formulário', async () => {
    renderAsTeacher(routes, '/my-students/0190a000-0000-7000-8000-0000000000ff', {
      'GET /api/v1/my-students/0190a000-0000-7000-8000-0000000000ff': {
        status: 404,
        body: { code: 'RESOURCE_NOT_FOUND', message: 'Não encontramos o que você procurou.' },
      },
    });
    expect(await screen.findByRole('alert')).toHaveTextContent('Não encontramos o que você procurou.');
    expect(screen.queryByLabelText('CPF')).not.toBeInTheDocument();
  });

  it('salva sem CPF quando o campo fica em branco e avisa o sucesso', async () => {
    const user = userEvent.setup();
    const saved = { ...MY_STUDENT_ACTIVE, instrument: 'Guitarra' };
    const api = renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_ACTIVE.id}`,
      detail(MY_STUDENT_ACTIVE, { [`PATCH /api/v1/my-students/${MY_STUDENT_ACTIVE.id}`]: { body: saved } }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' });
    await user.clear(screen.getByLabelText('Instrumento'));
    await user.type(screen.getByLabelText('Instrumento'), 'Guitarra');
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));

    expect(await screen.findByText(msg('REGISTRY_UPDATED', { name: 'Olavo Torrente' }))).toBeInTheDocument();
    const patch = api.calls.find((c) => c.method === 'PATCH')!;
    const body = bodyOf(patch);
    expect(body).toMatchObject({ name: 'Olavo Torrente', instrument: 'Guitarra' });
    expect('cpf' in body).toBe(false);
    expect((patch.init.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('test-token');
  });

  it('envia o CPF novo só no corpo, sem deixá-lo na URL nem no cache de consulta', async () => {
    const user = userEvent.setup();
    const { calls, router, client } = renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_ACTIVE.id}`,
      detail(MY_STUDENT_ACTIVE, {
        [`PATCH /api/v1/my-students/${MY_STUDENT_ACTIVE.id}`]: { body: MY_STUDENT_ACTIVE },
      }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF_MASKED);
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    await screen.findByText(msg('REGISTRY_UPDATED', { name: 'Olavo Torrente' }));

    expect(bodyOf(calls.find((c) => c.method === 'PATCH')!).cpf).toBe(TEST_CPF_MASKED);
    expect(calls.map((c) => c.url).join(' ')).not.toContain(TEST_CPF);
    expect(router.state.location.search).toBe('');
    expect(
      JSON.stringify(
        client
          .getQueryCache()
          .getAll()
          .map((q) => q.queryKey),
      ),
    ).not.toContain(TEST_CPF);
    // depois de salvar, o CPF digitado some do campo
    expect(screen.getByLabelText('CPF')).toHaveValue('');
  });

  it('erros por campo e CPF indisponível aparecem no formulário', async () => {
    const user = userEvent.setup();
    renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_ACTIVE.id}`,
      detail(MY_STUDENT_ACTIVE, {
        [`PATCH /api/v1/my-students/${MY_STUDENT_ACTIVE.id}`]: {
          status: 400,
          body: validationError([{ field: 'birthDate', code: 'INVALID_BIRTH_DATE' }]),
        },
      }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' });
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    expect(await screen.findByRole('alert')).toHaveFocus();
    expect(screen.getByLabelText('Data de nascimento')).toHaveAccessibleDescription(
      msg('INVALID_BIRTH_DATE'),
    );
  });

  it('CPF de aluno alheio na edição mostra só a mensagem neutra no campo', async () => {
    const user = userEvent.setup();
    renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_ACTIVE.id}`,
      detail(MY_STUDENT_ACTIVE, {
        [`PATCH /api/v1/my-students/${MY_STUDENT_ACTIVE.id}`]: {
          status: 409,
          body: { code: 'CPF_UNAVAILABLE', message: 'x', details: { name: 'Fulano Alheio' } },
        },
      }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    await waitFor(() =>
      expect(screen.getByLabelText('CPF')).toHaveAccessibleDescription(
        `${msg('CPF_UNAVAILABLE')} ${msg('MY_STUDENTS_CPF_KEEP')}`,
      ),
    );
    expect(document.body.textContent).not.toContain('Fulano Alheio');
  });

  it('bloqueio na edição desabilita só o envio; inativar continua disponível', async () => {
    const user = userEvent.setup();
    renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_ACTIVE.id}`,
      detail(MY_STUDENT_ACTIVE, {
        [`PATCH /api/v1/my-students/${MY_STUDENT_ACTIVE.id}`]: {
          status: 429,
          headers: { 'Retry-After': '120' },
          body: blockedError(120),
        },
      }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));

    expect(await screen.findByText(msg('OPERATION_TEMPORARILY_BLOCKED', { minutes: 2 }))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Salvar alterações' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Inativar aluno' })).toBeEnabled();
    expect(screen.getByLabelText('Nome do aluno')).toBeEnabled();
  });

  it('inativar pede confirmação com o texto do catálogo e só grava ao confirmar', async () => {
    const user = userEvent.setup();
    const inactive = { ...MY_STUDENT_ACTIVE, active: false };
    const api = renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_ACTIVE.id}`,
      detail(MY_STUDENT_ACTIVE, {
        [`POST /api/v1/my-students/${MY_STUDENT_ACTIVE.id}/inactivation`]: { body: inactive },
      }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' });

    await user.click(screen.getByRole('button', { name: 'Inativar aluno' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(msg('MY_STUDENTS_CONFIRM_INACTIVATE', { name: 'Olavo Torrente' }));

    // voltar não grava
    await user.click(within(dialog).getByRole('button', { name: 'Voltar' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(api.calls.some((c) => c.method === 'POST')).toBe(false);

    await user.click(screen.getByRole('button', { name: 'Inativar aluno' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Inativar' }));

    expect(
      await screen.findByText(msg('STUDENT_INACTIVATED', { name: 'Olavo Torrente' })),
    ).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(api.calls.filter((c) => c.method === 'POST')).toHaveLength(1);
    expect(await screen.findByRole('button', { name: 'Reativar aluno' })).toBeEnabled();
    expect(screen.getByText('Inativo')).toBeInTheDocument();
  });

  it('reativar não pede confirmação e avisa o sucesso', async () => {
    const user = userEvent.setup();
    const active = { ...MY_STUDENT_INACTIVE, active: true };
    renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_INACTIVE.id}`,
      detail(MY_STUDENT_INACTIVE, {
        [`POST /api/v1/my-students/${MY_STUDENT_INACTIVE.id}/reactivation`]: { body: active },
      }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Helena Prado' });
    expect(screen.getByText('Inativo')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reativar aluno' }));
    expect(await screen.findByText(msg('STUDENT_REACTIVATED', { name: 'Helena Prado' }))).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Inativar aluno' })).toBeEnabled();
  });

  it('aluno compartilhado: botão desabilitado com a orientação, mas a edição segue disponível', async () => {
    renderAsTeacher(routes, `/my-students/${MY_STUDENT_SHARED.id}`, detail(MY_STUDENT_SHARED));
    await screen.findByRole('heading', { level: 1, name: 'Miguel de Castro' });
    const button = screen.getByRole('button', { name: 'Inativar aluno' });
    expect(button).toBeDisabled();
    expect(button).toHaveAccessibleDescription(msg('SHARED_STUDENT_ACTION_DENIED'));
    expect(screen.getByText(msg('SHARED_STUDENT_ACTION_DENIED'))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Salvar alterações' })).toBeEnabled();
  });

  it('aluno compartilhado e inativo: reativar também fica desabilitado', async () => {
    renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_SHARED.id}`,
      detail({ ...MY_STUDENT_SHARED, active: false }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Miguel de Castro' });
    expect(screen.getByRole('button', { name: 'Reativar aluno' })).toBeDisabled();
  });

  it('se o servidor negar mesmo assim, mostra SHARED_STUDENT_ACTION_DENIED na confirmação', async () => {
    const user = userEvent.setup();
    renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_ACTIVE.id}`,
      detail(MY_STUDENT_ACTIVE, {
        [`POST /api/v1/my-students/${MY_STUDENT_ACTIVE.id}/inactivation`]: {
          status: 403,
          body: { code: 'SHARED_STUDENT_ACTION_DENIED', message: msg('SHARED_STUDENT_ACTION_DENIED') },
        },
      }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' });
    await user.click(screen.getByRole('button', { name: 'Inativar aluno' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Inativar' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(msg('SHARED_STUDENT_ACTION_DENIED'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('Cancelar volta para a lista', async () => {
    const user = userEvent.setup();
    const { router } = renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_ACTIVE.id}`,
      detail(MY_STUDENT_ACTIVE),
    );
    await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' });
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/my-students'));
  });

  it('erro de situação mostra o texto do catálogo pelo código, nunca a mensagem crua do servidor', async () => {
    const user = userEvent.setup();
    renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_ACTIVE.id}`,
      detail(MY_STUDENT_ACTIVE, {
        [`POST /api/v1/my-students/${MY_STUDENT_ACTIVE.id}/inactivation`]: {
          status: 409,
          body: { code: 'SHARED_STUDENT_ACTION_DENIED', message: 'texto cru: professores Fulano e Beltrano' },
        },
      }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' });
    await user.click(screen.getByRole('button', { name: 'Inativar aluno' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Inativar' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(msg('SHARED_STUDENT_ACTION_DENIED'));
    expect(document.body.textContent).not.toContain('Beltrano');
  });

  it('aluno que não carrega mostra o texto do catálogo pelo código', async () => {
    renderAsTeacher(routes, `/my-students/${MY_STUDENT_ACTIVE.id}`, {
      'GET /api/v1/my-students': { body: myStudentsPage([]) },
      [`GET /api/v1/my-students/${MY_STUDENT_ACTIVE.id}`]: {
        status: 404,
        body: { code: 'RESOURCE_NOT_FOUND', message: 'texto cru do servidor' },
      },
    });
    expect(await screen.findByRole('alert')).toHaveTextContent(msg('RESOURCE_NOT_FOUND'));
    expect(document.body.textContent).not.toContain('texto cru do servidor');
  });

  it('erro de gravação: a mutação é zerada e o CPF não fica nas variáveis em memória', async () => {
    const user = userEvent.setup();
    const api = renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_ACTIVE.id}`,
      detail(MY_STUDENT_ACTIVE, {
        [`PATCH /api/v1/my-students/${MY_STUDENT_ACTIVE.id}`]: {
          status: 400,
          body: validationError([{ field: 'cpf', code: 'INVALID_CPF' }]),
        },
      }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));

    await waitFor(() =>
      expect(screen.getByLabelText('CPF')).toHaveAccessibleDescription(
        `${msg('INVALID_CPF')} ${msg('MY_STUDENTS_CPF_KEEP')}`,
      ),
    );
    expect(
      JSON.stringify(
        api.client
          .getMutationCache()
          .getAll()
          .map((m) => m.state.variables ?? null),
      ),
    ).not.toContain(TEST_CPF);
  });

  it('pendente mostra a situação e o botão; registrado mostra só a situação; adulto não mostra a seção', async () => {
    const id = MY_STUDENT_ACTIVE.id;
    const status = `GET /api/v1/my-students/${id}/consent-status`;

    const pending = renderAsTeacher(
      routes,
      `/my-students/${id}`,
      detail(MY_STUDENT_ACTIVE, { [status]: { body: { required: true, status: 'MISSING' } } }),
    );
    await screen.findByText(msg('CONSENT_STATUS_PENDING'));
    expect(screen.getByRole('button', { name: msg('CONSENT_RECORD_BUTTON') })).toBeInTheDocument();
    pending.unmount();

    const valid = renderAsTeacher(
      routes,
      `/my-students/${id}`,
      detail(MY_STUDENT_ACTIVE, { [status]: { body: { required: true, status: 'VALID' } } }),
    );
    await screen.findByText(msg('CONSENT_STATUS_VALID'));
    expect(screen.queryByRole('button', { name: msg('CONSENT_RECORD_BUTTON') })).not.toBeInTheDocument();
    valid.unmount();

    renderAsTeacher(
      routes,
      `/my-students/${id}`,
      detail(MY_STUDENT_ACTIVE, { [status]: { body: { required: false } } }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' });
    expect(screen.queryByText(msg('CONSENT_STATUS_LABEL'))).not.toBeInTheDocument();
  });

  it('registrar o consentimento pelo botão envia o termo vigente e avisa o sucesso', async () => {
    const user = userEvent.setup();
    const id = MY_STUDENT_ACTIVE.id;
    const api = renderAsTeacher(
      routes,
      `/my-students/${id}`,
      detail(MY_STUDENT_ACTIVE, {
        [`GET /api/v1/my-students/${id}/consent-status`]: { body: { required: true, status: 'MISSING' } },
        'GET /api/v1/my-students/consent-term': {
          body: {
            number: 3,
            publishedOn: '2026-09-01',
            text: 'Institutional text',
            privacyContact: { name: 'Officer', contact: 'officer@example.test' },
          },
        },
        [`POST /api/v1/my-students/${id}/consents`]: {
          status: 201,
          body: { status: 'VALID', termVersion: 3, consentedOn: '2026-09-21' },
        },
      }),
    );
    await user.click(await screen.findByRole('button', { name: msg('CONSENT_RECORD_BUTTON') }));
    const panel = within(screen.getByRole('form', { name: msg('CONSENT_RECORD_TITLE') }));
    await user.type(panel.getByLabelText('Nome de quem consentiu'), 'Maria Guardian');
    await user.selectOptions(panel.getByLabelText('Relação com o aluno'), 'MOTHER');
    await user.selectOptions(panel.getByLabelText('Forma de coleta'), 'SIGNED_PAPER');
    await user.click(panel.getByRole('button', { name: msg('CONSENT_RECORD_BUTTON') }));

    expect(await screen.findByText(msg('CONSENT_RECORDED_NOTICE'))).toBeInTheDocument();
    const post = api.calls.find((c) => c.method === 'POST')!;
    expect(bodyOf(post)).toMatchObject({
      consentingName: 'Maria Guardian',
      relationship: 'MOTHER',
      termVersion: 3,
      collectionMethod: 'SIGNED_PAPER',
    });
    expect(
      JSON.stringify(
        api.client
          .getMutationCache()
          .getAll()
          .map((m) => m.state.variables ?? null),
      ),
    ).not.toContain('Maria Guardian');
  });

  it('gravação com sucesso também zera a mutação (o CPF não fica nas variáveis em memória)', async () => {
    const user = userEvent.setup();
    const api = renderAsTeacher(
      routes,
      `/my-students/${MY_STUDENT_ACTIVE.id}`,
      detail(MY_STUDENT_ACTIVE, {
        [`PATCH /api/v1/my-students/${MY_STUDENT_ACTIVE.id}`]: { body: MY_STUDENT_ACTIVE },
      }),
    );
    await screen.findByRole('heading', { level: 1, name: 'Olavo Torrente' });
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    await screen.findByText(msg('REGISTRY_UPDATED', { name: 'Olavo Torrente' }));
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
