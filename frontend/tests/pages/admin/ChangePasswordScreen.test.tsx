import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { validationError } from '../../support/adminFixtures';
import { LIVE_ADMIN_SESSION, renderAdminArea } from './adminTestSupport';

const SESSION = 'GET /api/v1/admin/session';
const CHANGE = 'PUT /api/v1/admin/password';
const PROVISIONAL_SESSION = { ...LIVE_ADMIN_SESSION, mustChangePassword: true };
const NEW_PASSWORD = 'a-long-valid-password';

async function typePasswords(
  user: ReturnType<typeof userEvent.setup>,
  password: string,
  confirmation: string,
) {
  await user.type(await screen.findByLabelText('Nova senha'), password);
  await user.type(screen.getByLabelText('Confirme a nova senha'), confirmation);
}

describe('ChangePasswordScreen (A03)', () => {
  it('pede só a nova senha e a confirmação, com a regra de tamanho visível', async () => {
    renderAdminArea('/admin/change-password', { [SESSION]: { body: PROVISIONAL_SESSION } });
    expect(await screen.findByRole('heading', { name: 'Defina uma nova senha' })).toBeInTheDocument();
    expect(screen.getByLabelText('Nova senha')).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getByLabelText('Confirme a nova senha')).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getAllByLabelText(/senha/i)).toHaveLength(2);
    expect(screen.queryByLabelText('E-mail')).not.toBeInTheDocument();
    expect(screen.getByText('A senha deve ter de 12 a 128 caracteres.')).toBeInTheDocument();
    expect(screen.queryByRole('meter')).not.toBeInTheDocument();
  });

  it('enquanto a senha for provisória, qualquer outra rota administrativa redireciona para cá', async () => {
    const { router } = renderAdminArea('/admin/teachers', { [SESSION]: { body: PROVISIONAL_SESSION } });
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/change-password'));
    expect(await screen.findByRole('heading', { name: 'Defina uma nova senha' })).toBeInTheDocument();
  });

  it('durante a troca a navegação some e "Sair" continua disponível', async () => {
    renderAdminArea('/admin/change-password', { [SESSION]: { body: PROVISIONAL_SESSION } });
    await screen.findByRole('heading', { name: 'Defina uma nova senha' });
    expect(screen.queryByRole('link', { name: 'Professores' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sair' })).toBeInTheDocument();
  });

  it('troca a senha e segue para o início com a sessão nova', async () => {
    let changed = false;
    const api = renderAdminArea('/admin/change-password', {
      [SESSION]: () => ({ body: changed ? LIVE_ADMIN_SESSION : PROVISIONAL_SESSION }),
      [CHANGE]: () => {
        changed = true;
        return { body: LIVE_ADMIN_SESSION };
      },
      'GET /api/v1/admin/teachers': { body: { items: [], page: 0, size: 1, total: 0 } },
      'GET /api/v1/admin/students': { body: { items: [], page: 0, size: 1, total: 0 } },
      'GET /api/v1/admin/administrators': { body: { items: [], page: 0, size: 1, total: 0 } },
    });
    const user = userEvent.setup();
    await typePasswords(user, NEW_PASSWORD, NEW_PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Trocar senha' }));

    await waitFor(() => expect(api.router.state.location.pathname).toBe('/admin'));
    const put = api.calls.find((call) => call.method === 'PUT')!;
    expect(JSON.parse(put.init.body as string)).toEqual({ newPassword: NEW_PASSWORD });
    expect(document.body.textContent).not.toContain(NEW_PASSWORD);
  });

  it('confirmação diferente ou vazia não chama o servidor', async () => {
    const api = renderAdminArea('/admin/change-password', { [SESSION]: { body: PROVISIONAL_SESSION } });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Trocar senha' }));
    expect(screen.getAllByText('Informe a nova senha.').length).toBeGreaterThan(0);

    await typePasswords(user, NEW_PASSWORD, 'a-different-password');
    await user.click(screen.getByRole('button', { name: 'Trocar senha' }));
    expect(screen.getAllByText('As senhas não são iguais.').length).toBeGreaterThan(0);
    expect(api.calls.some((call) => call.method === 'PUT')).toBe(false);
  });

  it('senha igual à provisória mostra o texto do catálogo no campo e limpa os valores', async () => {
    renderAdminArea('/admin/change-password', {
      [SESSION]: { body: PROVISIONAL_SESSION },
      [CHANGE]: {
        status: 400,
        body: validationError([{ field: 'newPassword', code: 'PASSWORD_SAME_AS_PROVISIONAL' }]),
      },
    });
    const user = userEvent.setup();
    await typePasswords(user, NEW_PASSWORD, NEW_PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Trocar senha' }));

    expect(
      (await screen.findAllByText('A nova senha deve ser diferente da senha provisória.')).length,
    ).toBeGreaterThan(0);
    expect(screen.getByLabelText('Nova senha')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Nova senha')).toHaveValue('');
    expect(screen.getByLabelText('Confirme a nova senha')).toHaveValue('');
  });

  it('senha já definida (409) mostra o texto do catálogo', async () => {
    renderAdminArea('/admin/change-password', {
      [SESSION]: { body: PROVISIONAL_SESSION },
      [CHANGE]: { status: 409, body: { code: 'PASSWORD_CHANGE_NOT_REQUIRED', message: 'x' } },
    });
    const user = userEvent.setup();
    await typePasswords(user, NEW_PASSWORD, NEW_PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Trocar senha' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Sua senha já foi definida.');
  });

  it('quem já definiu a senha volta ao início', async () => {
    const { router } = renderAdminArea('/admin/change-password', {
      'GET /api/v1/admin/teachers': { body: { items: [], page: 0, size: 1, total: 0 } },
      'GET /api/v1/admin/students': { body: { items: [], page: 0, size: 1, total: 0 } },
      'GET /api/v1/admin/administrators': { body: { items: [], page: 0, size: 1, total: 0 } },
    });
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin'));
  });

  it('depois da resposta (erro de campo ou geral) a senha não fica nas variáveis da mutação e o erro continua na tela', async () => {
    const api = renderAdminArea('/admin/change-password', {
      [SESSION]: { body: PROVISIONAL_SESSION },
      [CHANGE]: { status: 409, body: { code: 'PASSWORD_CHANGE_NOT_REQUIRED', message: 'x' } },
    });
    const user = userEvent.setup();
    await typePasswords(user, NEW_PASSWORD, NEW_PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Trocar senha' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Sua senha já foi definida.');
    expect(
      JSON.stringify(
        api.client
          .getMutationCache()
          .getAll()
          .map((mutation) => mutation.state.variables ?? null),
      ),
    ).not.toContain(NEW_PASSWORD);
  });
});
