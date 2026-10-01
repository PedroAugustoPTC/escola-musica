import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rotas } from '../../src/app/rotas';
import { SESSAO_CLARA, renderizarRotas, simularApi } from '../utilitarios';

const ALUNO = '0190a000-0000-7000-8000-00000000000a';
const SEMESTRAL = '0190a000-0000-7000-8000-0000000000s1';

const SEMESTRES = [
  {
    chave: '2026-2',
    rotulo: '2º semestre 2026',
    inicio: '2026-07-01',
    fim: '2026-12-31',
    emAndamento: true,
    preSelecionado: true,
  },
  {
    chave: '2026-1',
    rotulo: '1º semestre 2026',
    inicio: '2026-01-01',
    fim: '2026-06-30',
    emAndamento: false,
    preSelecionado: false,
  },
];

function previa(chave: string, sobrescrever: Record<string, unknown> = {}) {
  return {
    periodoChave: chave,
    aulasAprovadas: 3,
    relatoriosAulaEmAberto: 0,
    semestralAprovadoId: null,
    semestralEmAberto: null,
    ...sobrescrever,
  };
}

const BASE = {
  'GET /api/v1/sessao': { corpo: SESSAO_CLARA },
  'GET /api/v1/inicio': {
    corpo: {
      professor: SESSAO_CLARA.professor,
      totalAlunos: 1,
      alunos: [],
      relatoriosEmAberto: [],
      limiteAudioMb: 50,
    },
  },
  [`GET /api/v1/alunos/${ALUNO}`]: {
    corpo: {
      id: ALUNO,
      nome: 'Matteo Mafra',
      instrumento: 'Piano',
      ultimaAulaEm: null,
      possuiRelatorioEmAberto: false,
    },
  },
  [`GET /api/v1/alunos/${ALUNO}/semestres`]: { corpo: SEMESTRES },
};

describe('TelaSemestral (10)', () => {
  it('pré-seleciona o mais recente, mostra o intervalo e gera pelo semestre escolhido', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/alunos/${ALUNO}/semestres/2026-2/previa`]: {
        corpo: previa('2026-2', { aulasAprovadas: 0 }),
      },
      [`GET /api/v1/alunos/${ALUNO}/semestres/2026-1/previa`]: { corpo: previa('2026-1') },
      [`POST /api/v1/alunos/${ALUNO}/relatorios-semestrais`]: { status: 202, corpo: { trabalhoId: 't7' } },
      'GET /api/v1/trabalhos/t7': {
        corpo: {
          trabalhoId: 't7',
          tipo: 'CONSOLIDACAO_SEMESTRAL',
          status: 'EM_EXECUCAO',
          etapa: 'CONSOLIDANDO',
          demorando: false,
          resultadoCodigo: null,
          relatorioTipo: 'SEMESTRAL',
          relatorioId: null,
          textoEntendido: null,
        },
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/alunos/${ALUNO}/semestral`);

    const grupo = await screen.findByRole('radiogroup', { name: 'Período' });
    expect(within(grupo).getByRole('radio', { name: /2º semestre 2026/ })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(screen.getByText('2º semestre 2026: 01/07/2026 a 31/12/2026')).toBeInTheDocument();
    // 10b: sem aulas aprovadas no período
    expect(await screen.findByText(/Nenhum relatório de aula aprovado neste período/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gerar relatório semestral' })).toBeDisabled();

    await usuario.click(within(grupo).getByRole('radio', { name: /1º semestre 2026/ }));
    expect(
      await screen.findByText('O 1º semestre 2026 tem 3 relatório(s) de aula aprovado(s).'),
    ).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Gerar relatório semestral' }));

    await waitFor(() => expect(roteador.state.location.pathname).toBe('/trabalhos/t7'));
    const envio = api.chamadas.find((c) => c.metodo === 'POST')!;
    expect(JSON.parse(envio.init.body as string)).toEqual({
      periodoChave: '2026-1',
      confirmarMesmoComEmAberto: false,
    });
  });

  it('relatórios de aula em aberto pedem "Gerar mesmo assim"', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/alunos/${ALUNO}/semestres/2026-2/previa`]: {
        corpo: previa('2026-2', { relatoriosAulaEmAberto: 2 }),
      },
      [`POST /api/v1/alunos/${ALUNO}/relatorios-semestrais`]: { status: 202, corpo: { trabalhoId: 't8' } },
    });
    const usuario = userEvent.setup();
    renderizarRotas(rotas, `/alunos/${ALUNO}/semestral`);

    expect(
      await screen.findByText(
        'Há 2 relatório(s) de aula em aberto neste período; eles não serão considerados.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gerar relatório semestral' })).toBeDisabled();
    await usuario.click(screen.getByRole('button', { name: 'Gerar mesmo assim' }));

    await waitFor(() => expect(api.chamadas.some((c) => c.metodo === 'POST')).toBe(true));
    const envio = api.chamadas.find((c) => c.metodo === 'POST')!;
    expect(JSON.parse(envio.init.body as string)).toEqual({
      periodoChave: '2026-2',
      confirmarMesmoComEmAberto: true,
    });
  });

  it('semestral já aprovado e semestral em aberto com continuar ou descartar', async () => {
    let cancelado = false;
    simularApi({
      ...BASE,
      [`GET /api/v1/alunos/${ALUNO}/semestres/2026-2/previa`]: () => ({
        corpo: previa('2026-2', {
          semestralEmAberto: cancelado
            ? null
            : {
                tipo: 'SEMESTRAL',
                relatorioId: SEMESTRAL,
                trabalhoId: null,
                alunoId: ALUNO,
                alunoNome: 'Matteo Mafra',
                status: 'AGUARDANDO_CONFERENCIA',
                etapa: null,
                periodoChave: '2026-2',
                canalOrigem: 'TELEGRAM',
                atualizadoEm: '2026-09-20T12:57:00Z',
              },
        }),
      }),
      [`GET /api/v1/alunos/${ALUNO}/semestres/2026-1/previa`]: {
        corpo: previa('2026-1', { semestralAprovadoId: SEMESTRAL }),
      },
      [`POST /api/v1/relatorios-semestrais/${SEMESTRAL}/cancelamento`]: () => {
        cancelado = true;
        return { status: 204 };
      },
    });
    const usuario = userEvent.setup();
    renderizarRotas(rotas, `/alunos/${ALUNO}/semestral`);

    expect(await screen.findByText(/iniciado pelo Telegram e ainda não concluído/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continuar de onde parei' })).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Descartar e começar um novo' }));
    await usuario.click(screen.getByRole('button', { name: 'Sim, descartar' }));
    expect(
      await screen.findByText('O 2º semestre 2026 tem 3 relatório(s) de aula aprovado(s).'),
    ).toBeInTheDocument();

    await usuario.click(screen.getByRole('radio', { name: /1º semestre 2026/ }));
    expect(
      await screen.findByText('O relatório semestral deste período já foi aprovado.'),
    ).toBeInTheDocument();
  });

  it('10c: nenhum semestre disponível', async () => {
    simularApi({ ...BASE, [`GET /api/v1/alunos/${ALUNO}/semestres`]: { corpo: [] } });
    renderizarRotas(rotas, `/alunos/${ALUNO}/semestral`);

    expect(await screen.findByText('Nenhum semestre disponível para este aluno ainda.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Gerar relatório semestral' })).not.toBeInTheDocument();
  });
});
