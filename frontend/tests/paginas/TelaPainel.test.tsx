import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rotas } from '../../src/app/rotas';
import { SESSAO_CLARA, renderizarRotas, simularApi } from '../utilitarios';

const MATTEO = {
  id: '0190a000-0000-7000-8000-00000000000a',
  name: 'Matteo Mafra',
  instrument: 'Piano',
  lastLessonAt: '2026-09-21',
  hasOpenReport: true,
};

const INICIO = {
  teacher: SESSAO_CLARA.teacher,
  totalStudents: 7,
  students: [MATTEO],
  openReports: [
    {
      type: 'LESSON',
      reportId: '0190a000-0000-7000-8000-0000000000aa',
      jobId: null,
      studentId: MATTEO.id,
      studentName: 'Matteo Mafra',
      status: 'AWAITING_REVIEW',
      step: null,
      periodKey: null,
      originChannel: 'TELEGRAM',
      updatedAt: '2026-09-20T12:57:00Z',
    },
  ],
  audioLimitMb: 50,
};

describe('TelaPainel (02)', () => {
  it('mostra saudação, relatórios em aberto, total e primeiros alunos', async () => {
    simularApi({ 'GET /api/v1/session': { corpo: SESSAO_CLARA }, 'GET /api/v1/home': { corpo: INICIO } });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, '/');

    expect(await screen.findByRole('heading', { name: 'Olá, Clara' })).toBeInTheDocument();
    const aviso = screen.getByRole('status');
    expect(aviso).toHaveTextContent('Matteo Mafra · Relatório de aula · Aguardando conferência');
    expect(aviso).toHaveTextContent('última alteração em 20/09/2026 09:57');
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('Piano · última aula 21/09/2026')).toBeInTheDocument();
    expect(screen.getByText('Em aberto')).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: /Continuar: Matteo Mafra/ }));
    expect(roteador.state.location.pathname).toBe(`/alunos/${MATTEO.id}/relatorio-em-aberto`);
  });

  it('registrar aula leva à escolha do aluno', async () => {
    simularApi({ 'GET /api/v1/session': { corpo: SESSAO_CLARA }, 'GET /api/v1/home': { corpo: INICIO } });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, '/');
    await usuario.click(await screen.findByRole('button', { name: /Grave um áudio/ }));
    expect(roteador.state.location.search).toBe('?registrar=1');
  });

  it('vazio (02b) quando não há alunos', async () => {
    simularApi({
      'GET /api/v1/session': { corpo: SESSAO_CLARA },
      'GET /api/v1/home': { corpo: { ...INICIO, totalStudents: 0, students: [], openReports: [] } },
    });
    renderizarRotas(rotas, '/');
    expect(await screen.findByText('Nenhum aluno por aqui')).toBeInTheDocument();
    expect(
      screen.getByText('Você ainda não tem alunos associados. Fale com a secretaria da escola.'),
    ).toBeInTheDocument();
  });

  it('menu (02c) com tema e encerrar sessão; 401 leva a Entrar', async () => {
    simularApi({
      'GET /api/v1/session': { corpo: SESSAO_CLARA },
      'GET /api/v1/home': {
        status: 401,
        corpo: {
          code: 'SESSION_EXPIRED',
          message: 'Sua sessão expirou. Entre novamente com o seu código.',
        },
      },
    });
    const { roteador } = renderizarRotas(rotas, '/');
    await waitFor(() => expect(roteador.state.location.pathname).toBe('/entrar'));
    expect(roteador.state.location.search).toContain('motivo=SESSION_EXPIRED');
  });
});
