import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { adminRoutes } from '../../../src/app/adminRoutes';
import { mockApi, renderRoutes } from '../../utils';
import { ADMIN_SESSION_PASSWORD_CHANGE_REQUIRED, blockedError } from '../../support/adminFixtures';
import { LIVE_ADMIN_SESSION as ADMIN_SESSION } from './adminTestSupport';

const NOT_AUTHENTICATED = { status: 401, body: { code: 'NOT_AUTHENTICATED', message: 'x' } };
const WRONG_CREDENTIALS = 'E-mail ou senha incorretos.';

async function fillAndSend(user: ReturnType<typeof userEvent.setup>, email: string, password: string) {
  await user.type(screen.getByLabelText('E-mail'), email);
  await user.type(screen.getByLabelText('Senha'), password);
  await user.click(screen.getByRole('button', { name: 'Entrar' }));
}

describe('AdminSignInScreen (A01)', () => {
  it('mostra o formulário com os atributos de preenchimento corretos e o link de ativação', async () => {
    mockApi({ 'GET /api/v1/admin/session': NOT_AUTHENTICATED });
    renderRoutes(adminRoutes, '/admin/sign-in');

    expect(await screen.findByRole('heading', { name: 'Entrar na área administrativa' })).toBeInTheDocument();
    expect(screen.getByLabelText('E-mail')).toHaveAttribute('autocomplete', 'username');
    expect(screen.getByLabelText('E-mail')).toHaveAttribute('type', 'email');
    expect(screen.getByLabelText('Senha')).toHaveAttribute('autocomplete', 'current-password');
    expect(screen.getByLabelText('Senha')).toHaveAttribute('type', 'password');
    expect(screen.getByRole('link', { name: 'Tenho um código de ativação' })).toHaveAttribute(
      'href',
      '/admin/activate',
    );
  });

  it('entra e volta ao destino guardado da área administrativa', async () => {
    let signedIn = false;
    const api = mockApi({
      'GET /api/v1/admin/session': () => (signedIn ? { body: ADMIN_SESSION } : NOT_AUTHENTICATED),
      'POST /api/v1/admin/session': () => {
        signedIn = true;
        return { body: ADMIN_SESSION };
      },
      'GET /api/v1/admin/teachers': { body: { items: [], page: 0, size: 20, total: 0 } },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(adminRoutes, '/admin/teachers');

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/sign-in'));
    expect(router.state.location.search).toBe('?destination=%2Fadmin%2Fteachers');
    await screen.findByLabelText('E-mail');

    await fillAndSend(user, 'marina@example.test', 'a-long-valid-password');

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/teachers'));
    const post = api.calls.find((call) => call.method === 'POST');
    expect(JSON.parse(post!.init.body as string)).toEqual({
      email: 'marina@example.test',
      password: 'a-long-valid-password',
    });
    expect((post!.init.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('test-token');
  });

  it.each(['//other.example.test', '/students', 'https://other.example.test/admin'])(
    'ignora o destino fora da área administrativa (%s)',
    async (destination) => {
      let signedIn = false;
      mockApi({
        'GET /api/v1/admin/session': () => (signedIn ? { body: ADMIN_SESSION } : NOT_AUTHENTICATED),
        'POST /api/v1/admin/session': () => {
          signedIn = true;
          return { body: ADMIN_SESSION };
        },
      });
      const user = userEvent.setup();
      const { router } = renderRoutes(
        adminRoutes,
        `/admin/sign-in?destination=${encodeURIComponent(destination)}`,
      );
      await screen.findByLabelText('E-mail');
      await fillAndSend(user, 'marina@example.test', 'a-long-valid-password');
      await waitFor(() => expect(router.state.location.pathname).toBe('/admin'));
    },
  );

  it('com senha provisória vai direto à troca de senha', async () => {
    let signedIn = false;
    mockApi({
      'GET /api/v1/admin/session': () =>
        signedIn ? { body: ADMIN_SESSION_PASSWORD_CHANGE_REQUIRED } : NOT_AUTHENTICATED,
      'POST /api/v1/admin/session': () => {
        signedIn = true;
        return { body: ADMIN_SESSION_PASSWORD_CHANGE_REQUIRED };
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(adminRoutes, '/admin/sign-in?destination=%2Fadmin%2Fteachers');
    await screen.findByLabelText('E-mail');
    await fillAndSend(user, 'marina@example.test', 'provisional-pass-123');
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/change-password'));
  });

  it.each([
    ['e-mail inexistente', { code: 'INVALID_CREDENTIALS', message: 'Texto do servidor A' }],
    ['senha errada', { code: 'INVALID_CREDENTIALS', message: 'Texto do servidor B' }],
    ['conta inativa', { code: 'INVALID_CREDENTIALS', message: '' }],
  ])('mostra uma mensagem única para qualquer falha (%s) e limpa a senha', async (_case, body) => {
    mockApi({
      'GET /api/v1/admin/session': NOT_AUTHENTICATED,
      'POST /api/v1/admin/session': { status: 401, body },
    });
    const user = userEvent.setup();
    renderRoutes(adminRoutes, '/admin/sign-in');
    await screen.findByLabelText('E-mail');
    await fillAndSend(user, 'alguem@example.test', 'any-password-123');

    expect(await screen.findByRole('alert')).toHaveTextContent(WRONG_CREDENTIALS);
    expect(screen.getByLabelText('Senha')).toHaveValue('');
    expect(screen.getByLabelText('E-mail')).toHaveValue('alguem@example.test');
  });

  it('campos vazios não chamam o servidor e indicam o que falta', async () => {
    const api = mockApi({ 'GET /api/v1/admin/session': NOT_AUTHENTICATED });
    const user = userEvent.setup();
    renderRoutes(adminRoutes, '/admin/sign-in');
    await screen.findByLabelText('E-mail');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(screen.getByText('Informe o e-mail.')).toBeInTheDocument();
    expect(screen.getByText('Informe a senha.')).toBeInTheDocument();
    expect(screen.getByLabelText('E-mail')).toHaveAttribute('aria-invalid', 'true');
    expect(api.calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('bloqueio mostra o tempo formatado em região aria-live, sem texto cru, e desabilita só o envio', async () => {
    mockApi({
      'GET /api/v1/admin/session': NOT_AUTHENTICATED,
      'POST /api/v1/admin/session': {
        status: 429,
        headers: { 'Retry-After': '125' },
        body: {
          ...blockedError(125),
          code: 'TEMPORARILY_BLOCKED',
          message: 'Tente em {minutes} minuto(s) {x}',
        },
      },
    });
    const user = userEvent.setup();
    renderRoutes(adminRoutes, '/admin/sign-in');
    await screen.findByLabelText('E-mail');
    await fillAndSend(user, 'marina@example.test', 'wrong-password-12345');

    const live = await screen.findByRole('status');
    expect(live).toHaveAttribute('aria-live', 'polite');
    await waitFor(() =>
      expect(live).toHaveTextContent(
        'Acesso bloqueado temporariamente por muitas tentativas incorretas. Tente novamente em 3 minuto(s).',
      ),
    );
    expect(document.body.textContent).not.toContain('{minutes}');
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeDisabled();
    expect(screen.getByLabelText('E-mail')).toBeEnabled();
    expect(screen.getByText(/0[0-2]:[0-5]\d/)).toBeInTheDocument();
  });

  it('o envio volta ao fim do bloqueio', async () => {
    mockApi({
      'GET /api/v1/admin/session': NOT_AUTHENTICATED,
      'POST /api/v1/admin/session': { status: 429, body: blockedError(2) },
    });
    const user = userEvent.setup();
    renderRoutes(adminRoutes, '/admin/sign-in');
    await screen.findByLabelText('E-mail');
    await fillAndSend(user, 'marina@example.test', 'wrong-password-12345');

    await waitFor(() => expect(screen.getByRole('button', { name: 'Entrar' })).toBeDisabled());
    await waitFor(() => expect(screen.getByRole('button', { name: 'Entrar' })).toBeEnabled(), {
      timeout: 5000,
    });
  });

  it('sem conexão e falha interna mostram o texto do catálogo', async () => {
    let attempt = 0;
    mockApi({
      'GET /api/v1/admin/session': NOT_AUTHENTICATED,
      'POST /api/v1/admin/session': () => {
        attempt += 1;
        if (attempt === 1) throw new TypeError('Failed to fetch');
        return { status: 500, body: { code: 'INTERNAL_ERROR', message: 'Ocorreu um erro inesperado.' } };
      },
    });
    const user = userEvent.setup();
    renderRoutes(adminRoutes, '/admin/sign-in');
    await screen.findByLabelText('E-mail');
    await fillAndSend(user, 'marina@example.test', 'any-password-123');
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível entrar agora. Verifique sua conexão e tente novamente.',
    );

    await user.type(screen.getByLabelText('Senha'), 'any-password-123');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Ocorreu um erro inesperado. Tente novamente mais tarde.',
      ),
    );
  });

  it('avisa que o acesso foi encerrado quando o motivo vem na URL', async () => {
    mockApi({ 'GET /api/v1/admin/session': NOT_AUTHENTICATED });
    renderRoutes(adminRoutes, '/admin/sign-in?reason=ACCESS_ENDED');
    expect(await screen.findByText('Seu acesso foi encerrado. Procure o administrador.')).toBeInTheDocument();
  });
});
