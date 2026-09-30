import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rotas } from '../../src/app/rotas';
import { SESSAO_CLARA, renderizarRotas, simularApi } from '../utilitarios';

const MATTEO = {
  id: '0190a000-0000-7000-8000-00000000000a',
  nome: 'Matteo Mafra',
  instrumento: 'Piano',
  ultimaAulaEm: '2026-09-21',
  possuiRelatorioEmAberto: true,
};

const INICIO = {
  professor: SESSAO_CLARA.professor,
  totalAlunos: 7,
  alunos: [MATTEO],
  relatoriosEmAberto: [
    {
      tipo: 'AULA',
      relatorioId: '0190a000-0000-7000-8000-0000000000aa',
      trabalhoId: null,
      alunoId: MATTEO.id,
      alunoNome: 'Matteo Mafra',
      status: 'AGUARDANDO_CONFERENCIA',
      etapa: null,
      periodoChave: null,
      canalOrigem: 'TELEGRAM',
      atualizadoEm: '2026-09-20T12:57:00Z',
    },
  ],
  limiteAudioMb: 50,
};

describe('TelaPainel (02)', () => {
  it('mostra saudação, relatórios em aberto, total e primeiros alunos', async () => {
    simularApi({ 'GET /api/v1/sessao': { corpo: SESSAO_CLARA }, 'GET /api/v1/inicio': { corpo: INICIO } });
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
    simularApi({ 'GET /api/v1/sessao': { corpo: SESSAO_CLARA }, 'GET /api/v1/inicio': { corpo: INICIO } });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, '/');
    await usuario.click(await screen.findByRole('button', { name: /Grave um áudio/ }));
    expect(roteador.state.location.search).toBe('?registrar=1');
  });

  it('vazio (02b) quando não há alunos', async () => {
    simularApi({
      'GET /api/v1/sessao': { corpo: SESSAO_CLARA },
      'GET /api/v1/inicio': { corpo: { ...INICIO, totalAlunos: 0, alunos: [], relatoriosEmAberto: [] } },
    });
    renderizarRotas(rotas, '/');
    expect(await screen.findByText('Nenhum aluno por aqui')).toBeInTheDocument();
    expect(
      screen.getByText('Você ainda não tem alunos associados. Fale com a secretaria da escola.'),
    ).toBeInTheDocument();
  });

  it('menu (02c) com tema e encerrar sessão; 401 leva a Entrar', async () => {
    simularApi({
      'GET /api/v1/sessao': { corpo: SESSAO_CLARA },
      'GET /api/v1/inicio': {
        status: 401,
        corpo: {
          codigo: 'SESSAO_EXPIRADA',
          mensagem: 'Sua sessão expirou. Entre novamente com o seu código.',
        },
      },
    });
    const { roteador } = renderizarRotas(rotas, '/');
    await waitFor(() => expect(roteador.state.location.pathname).toBe('/entrar'));
    expect(roteador.state.location.search).toContain('motivo=SESSAO_EXPIRADA');
  });
});
