import { describe, expect, it } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../src/app/routes';
import { LIGHT_SESSION, renderRoutes, mockApi } from '../utils';

const HOME = {
  teacher: LIGHT_SESSION.teacher,
  totalStudents: 0,
  students: [],
  openReports: [],
  audioLimitMb: 50,
};

describe('TelaEntrar (01)', () => {
  it('entra com o código e volta ao destino guardado', async () => {
    let authenticated = false;
    const api = mockApi({
      'GET /api/v1/session': () =>
        authenticated
          ? { body: LIGHT_SESSION }
          : {
              status: 401,
              body: {
                code: 'NOT_AUTHENTICATED',
                message: 'Entre com o seu código de acesso para continuar.',
              },
            },
      'POST /api/v1/session': () => {
        authenticated = true;
        return { body: LIGHT_SESSION };
      },
      'GET /api/v1/home': { body: HOME },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, '/students');

    await waitFor(() => expect(router.state.location.pathname).toBe('/sign-in'));
    expect(router.state.location.search).toBe('?destination=%2Fstudents');
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
    expect(screen.queryByText(/secretaria/)).not.toBeInTheDocument();

    const field = screen.getByLabelText('Código de acesso');
    await user.type(field, 'k7qm-2xpa');
    expect(field).toHaveValue('K7QM-2XPA');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/students'));
    const post = api.calls.find((c) => c.method === 'POST');
    expect(JSON.parse(post!.init.body as string)).toEqual({ code: 'K7QM-2XPA' });
    expect((post!.init.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('test-token');
  });

  it('código inválido (01b) mostra a mensagem do catálogo no campo', async () => {
    mockApi({
      'POST /api/v1/session': {
        status: 401,
        body: {
          code: 'INVALID_CODE',
          message:
            'Código inválido ou vencido. Procure o administrador.',
        },
      },
    });
    const user = userEvent.setup();
    renderRoutes(routes, '/sign-in');
    await user.type(screen.getByLabelText('Código de acesso'), 'ZZZZZZZZ');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Código inválido ou vencido. Procure o administrador.');
    expect(screen.getByLabelText('Código de acesso')).toHaveAttribute('aria-invalid', 'true');
  });

  it('bloqueio (01c) desabilita o campo e mostra o tempo restante', async () => {
    mockApi({
      'POST /api/v1/session': {
        status: 429,
        headers: { 'Retry-After': '120' },
        body: {
          code: 'TEMPORARILY_BLOCKED',
          message:
            'Acesso bloqueado temporariamente por muitas tentativas incorretas. Tente novamente em 2 minuto(s).',
          retryAfterSeconds: 120,
        },
      },
    });
    const user = userEvent.setup();
    renderRoutes(routes, '/sign-in');
    await user.type(screen.getByLabelText('Código de acesso'), 'K7QM2XPA');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    // folga maior que o padrão de 1 s: sob carga (suíte inteira em paralelo) a resposta pode atrasar
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(
      'Tente novamente em 2 minuto(s).',
    );
    expect(screen.getByLabelText('Código de acesso')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeDisabled();
  });

  it('sem conexão (01d)', async () => {
    mockApi({
      'POST /api/v1/session': () => {
        throw new TypeError('Failed to fetch');
      },
    });
    const user = userEvent.setup();
    renderRoutes(routes, '/sign-in');
    await user.type(screen.getByLabelText('Código de acesso'), 'K7QM2XPA');
    await act(async () => {
      await user.click(screen.getByRole('button', { name: 'Entrar' }));
    });
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível entrar agora. Verifique sua conexão e tente novamente.',
    );
  });

  it('avisa quando a sessão expirou', () => {
    mockApi({});
    renderRoutes(routes, '/sign-in?reason=SESSION_EXPIRED');
    expect(screen.getByRole('status')).toHaveTextContent('Sua sessão expirou.');
  });
});
