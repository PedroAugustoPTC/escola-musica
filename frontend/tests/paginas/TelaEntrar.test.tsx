import { describe, expect, it } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rotas } from '../../src/app/rotas';
import { SESSAO_CLARA, renderizarRotas, simularApi } from '../utilitarios';

const INICIO = {
  teacher: SESSAO_CLARA.teacher,
  totalStudents: 0,
  students: [],
  openReports: [],
  audioLimitMb: 50,
};

describe('TelaEntrar (01)', () => {
  it('entra com o código e volta ao destino guardado', async () => {
    let logado = false;
    const api = simularApi({
      'GET /api/v1/session': () =>
        logado
          ? { corpo: SESSAO_CLARA }
          : {
              status: 401,
              corpo: {
                code: 'NOT_AUTHENTICATED',
                message: 'Entre com o seu código de acesso para continuar.',
              },
            },
      'POST /api/v1/session': () => {
        logado = true;
        return { corpo: SESSAO_CLARA };
      },
      'GET /api/v1/home': { corpo: INICIO },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, '/alunos');

    await waitFor(() => expect(roteador.state.location.pathname).toBe('/entrar'));
    expect(roteador.state.location.search).toBe('?destino=%2Falunos');
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
    expect(screen.queryByText(/secretaria/)).not.toBeInTheDocument();

    const campo = screen.getByLabelText('Código de acesso');
    await usuario.type(campo, 'k7qm-2xpa');
    expect(campo).toHaveValue('K7QM-2XPA');
    await usuario.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => expect(roteador.state.location.pathname).toBe('/alunos'));
    const post = api.chamadas.find((c) => c.metodo === 'POST');
    expect(JSON.parse(post!.init.body as string)).toEqual({ code: 'K7QM-2XPA' });
    expect((post!.init.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('token-teste');
  });

  it('código inválido (01b) mostra a mensagem do catálogo no campo', async () => {
    simularApi({
      'POST /api/v1/session': {
        status: 401,
        corpo: {
          code: 'INVALID_CODE',
          message:
            'Código inválido. Confira o código e tente de novo, ou peça um novo à secretaria da escola.',
        },
      },
    });
    const usuario = userEvent.setup();
    renderizarRotas(rotas, '/entrar');
    await usuario.type(screen.getByLabelText('Código de acesso'), 'ZZZZZZZZ');
    await usuario.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Código inválido.');
    expect(screen.getByLabelText('Código de acesso')).toHaveAttribute('aria-invalid', 'true');
  });

  it('bloqueio (01c) desabilita o campo e mostra o tempo restante', async () => {
    simularApi({
      'POST /api/v1/session': {
        status: 429,
        cabecalhos: { 'Retry-After': '120' },
        corpo: {
          code: 'TEMPORARILY_BLOCKED',
          message:
            'Acesso bloqueado temporariamente por muitas tentativas incorretas. Tente novamente em 2 minuto(s).',
          retryAfterSeconds: 120,
        },
      },
    });
    const usuario = userEvent.setup();
    renderizarRotas(rotas, '/entrar');
    await usuario.type(screen.getByLabelText('Código de acesso'), 'K7QM2XPA');
    await usuario.click(screen.getByRole('button', { name: 'Entrar' }));

    // folga maior que o padrão de 1 s: sob carga (suíte inteira em paralelo) a resposta pode atrasar
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(
      'Tente novamente em 2 minuto(s).',
    );
    expect(screen.getByLabelText('Código de acesso')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeDisabled();
  });

  it('sem conexão (01d)', async () => {
    simularApi({
      'POST /api/v1/session': () => {
        throw new TypeError('Failed to fetch');
      },
    });
    const usuario = userEvent.setup();
    renderizarRotas(rotas, '/entrar');
    await usuario.type(screen.getByLabelText('Código de acesso'), 'K7QM2XPA');
    await act(async () => {
      await usuario.click(screen.getByRole('button', { name: 'Entrar' }));
    });
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível entrar agora. Verifique sua conexão e tente novamente.',
    );
  });

  it('avisa quando a sessão expirou', () => {
    simularApi({});
    renderizarRotas(rotas, '/entrar?motivo=SESSION_EXPIRED');
    expect(screen.getByRole('status')).toHaveTextContent('Sua sessão expirou.');
  });
});
