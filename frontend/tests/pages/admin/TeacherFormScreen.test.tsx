import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  TEACHER_ACCESS_CODE,
  TEACHER_ACTIVE,
  TEACHER_WITH_CODE,
  validationError,
} from '../../support/adminFixtures';
import { renderAdminArea } from './adminTestSupport';

const CREATE = 'POST /api/v1/admin/teachers';

async function fill(user: ReturnType<typeof userEvent.setup>, name: string, email: string) {
  await user.type(await screen.findByLabelText('Nome'), name);
  await user.type(screen.getByLabelText('E-mail'), email);
}

describe('TeacherFormScreen (A06/A07)', () => {
  it('cadastra o professor e mostra o código uma única vez, sem pôr o valor na URL nem no armazenamento', async () => {
    const api = renderAdminArea('/admin/teachers/new', {
      [CREATE]: { status: 201, body: TEACHER_WITH_CODE },
    });
    const user = userEvent.setup();
    await fill(user, 'Clara Souza', 'clara@example.test');
    await user.click(screen.getByRole('button', { name: 'Cadastrar' }));

    expect(await screen.findByText(TEACHER_ACCESS_CODE)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Professor cadastrado' })).toBeInTheDocument();
    const post = api.calls.find((call) => call.method === 'POST')!;
    expect(JSON.parse(post.init.body as string)).toEqual({
      name: 'Clara Souza',
      email: 'clara@example.test',
    });
    expect(api.router.state.location.pathname).toBe('/admin/teachers/new');
    expect(api.router.state.location.search).toBe('');
    expect(JSON.stringify({ ...window.localStorage })).not.toContain(TEACHER_ACCESS_CODE);
    expect(
      JSON.stringify(
        api.client
          .getMutationCache()
          .getAll()
          .map((m) => m.state.data ?? null),
      ),
    ).not.toContain(TEACHER_ACCESS_CODE);
    expect(
      JSON.stringify(
        api.client
          .getQueryCache()
          .getAll()
          .map((q) => q.state.data ?? null),
      ),
    ).not.toContain(TEACHER_ACCESS_CODE);
  });

  it('"Já anotei o código" apaga o valor e leva ao professor', async () => {
    const api = renderAdminArea('/admin/teachers/new', {
      [CREATE]: { status: 201, body: TEACHER_WITH_CODE },
      [`GET /api/v1/admin/teachers/${TEACHER_ACTIVE.id}`]: { body: TEACHER_ACTIVE },
    });
    const user = userEvent.setup();
    await fill(user, 'Clara Souza', 'clara@example.test');
    await user.click(screen.getByRole('button', { name: 'Cadastrar' }));
    await user.click(await screen.findByRole('button', { name: 'Já anotei o código' }));

    await waitFor(() =>
      expect(api.router.state.location.pathname).toBe(`/admin/teachers/${TEACHER_ACTIVE.id}`),
    );
    expect(document.body.textContent).not.toContain(TEACHER_ACCESS_CODE);
  });

  it('e-mail repetido mostra o nome do professor existente', async () => {
    renderAdminArea('/admin/teachers/new', {
      [CREATE]: {
        status: 409,
        body: { code: 'DUPLICATE_EMAIL', message: 'x', details: { name: 'Clara Souza' } },
      },
    });
    const user = userEvent.setup();
    await fill(user, 'Outra Clara', 'clara@example.test');
    await user.click(screen.getByRole('button', { name: 'Cadastrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Já existe um cadastro com este e-mail: Clara Souza.',
    );
    expect(screen.getByLabelText('E-mail')).toHaveAttribute('aria-invalid', 'true');
  });

  it('erros por campo ficam ligados por aria-describedby e o resumo recebe o foco', async () => {
    renderAdminArea('/admin/teachers/new', {
      [CREATE]: {
        status: 400,
        body: validationError([
          { field: 'name', code: 'INVALID_NAME' },
          { field: 'email', code: 'INVALID_EMAIL' },
        ]),
      },
    });
    const user = userEvent.setup();
    await fill(user, 'A', 'no-at-sign');
    await user.click(screen.getByRole('button', { name: 'Cadastrar' }));

    const summary = await screen.findByRole('alert');
    await waitFor(() => expect(summary).toHaveFocus());
    expect(summary).toHaveTextContent('O nome deve ter de 2 a 120 caracteres.');
    expect(summary).toHaveTextContent('Informe um e-mail válido, com até 254 caracteres.');
    const name = screen.getByLabelText('Nome');
    expect(name).toHaveAttribute('aria-invalid', 'true');
    const describedBy = name.getAttribute('aria-describedby')!;
    expect(document.getElementById(describedBy)).toHaveTextContent('O nome deve ter de 2 a 120 caracteres.');
  });

  it('campos vazios não chamam o servidor', async () => {
    const api = renderAdminArea('/admin/teachers/new');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Cadastrar' }));
    expect(screen.getAllByText('Informe o nome.').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Informe o e-mail.').length).toBeGreaterThan(0);
    expect(api.calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('falha ao salvar não avança a tela e mostra o erro do catálogo', async () => {
    renderAdminArea('/admin/teachers/new', {
      [CREATE]: { status: 500, body: { code: 'OPERATION_NOT_SAVED', message: 'x' } },
    });
    const user = userEvent.setup();
    await fill(user, 'Clara Souza', 'clara@example.test');
    await user.click(screen.getByRole('button', { name: 'Cadastrar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível concluir. Nada foi gravado; tente de novo.',
    );
    expect(screen.queryByRole('heading', { name: 'Professor cadastrado' })).not.toBeInTheDocument();
  });

  it('"Cancelar" volta à lista', async () => {
    const api = renderAdminArea('/admin/teachers/new', {
      'GET /api/v1/admin/teachers': { body: { items: [], page: 0, size: 20, total: 0 } },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('link', { name: 'Cancelar' }));
    await waitFor(() => expect(api.router.state.location.pathname).toBe('/admin/teachers'));
  });
});
