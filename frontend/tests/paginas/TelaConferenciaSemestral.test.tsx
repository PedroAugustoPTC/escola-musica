import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rotas } from '../../src/app/rotas';
import { SESSAO_CLARA, renderizarRotas, simularApi } from '../utilitarios';

const ID = '0190a000-0000-7000-8000-0000000000s1';

function relatorio(sobrescrever: Record<string, unknown> = {}) {
  return {
    relatorioId: ID,
    alunoId: '0190a000-0000-7000-8000-00000000000a',
    periodoChave: '2026-1',
    status: 'AGUARDANDO_CONFERENCIA',
    etapa: null,
    trabalhoEmCursoId: null,
    versaoVigente: 1,
    canalOrigem: 'WEB',
    informacoesGerais: {
      nome: 'Matteo Mafra',
      idade: 14,
      instrumento: 'Piano',
      professor: 'Clara Souza',
      periodoRotulo: '1º semestre 2026',
      periodoInicio: '2026-01-01',
      periodoFim: '2026-06-30',
      quantidadeAulasConsideradas: 12,
    },
    conteudo: {
      frequenciaEEstudo: {
        frequenciaAulas: 'Regular',
        regularidadeEstudoEmCasa: '',
        engajamento: '',
        comentarioGeral: '',
      },
      tecnica: {
        conteudosTrabalhados: ['Escalas'],
        evolucaoTecnica: '',
        pontosADesenvolver: [],
        estudosRealizados: [],
      },
      musicalidade: {},
      leituraEMemorizacao: {},
      pontosDeAtencao: { itens: [] },
      estrategiasPedagogicas: { itens: [] },
      acompanhamentoFamiliar: {},
      planejamentoProximoSemestre: {},
      parecerFinal: 'Bom semestre.',
    },
    quantidadeRelatoriosEmAberto: 2,
    ultimaInteracao: null,
    pdfDisponivel: true,
    aprovadoEm: null,
    atualizadoEm: '2026-09-21T13:00:00Z',
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
  [`GET /api/v1/relatorios-semestrais/${ID}/versoes/1/pdf`]: { corpo: new Blob(['%PDF-']) },
};

describe('TelaConferenciaSemestral (11)', () => {
  it('mostra as aulas consideradas, o aviso de em aberto e as 10 seções com Informações Gerais', async () => {
    simularApi({ ...BASE, [`GET /api/v1/relatorios-semestrais/${ID}`]: { corpo: relatorio() } });
    renderizarRotas(rotas, `/relatorios-semestrais/${ID}`);

    expect(
      await screen.findByText('12 relatórios de aula foram considerados nesta consolidação.'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Há 2 relatórios de aula em aberto neste período; eles não serão considerados.'),
    ).toBeInTheDocument();
    const texto = screen.getByRole('article', { name: 'Relatório em texto' });
    expect(within(texto).getAllByRole('heading', { level: 2 })).toHaveLength(10);
    expect(within(texto).getByText('14 anos')).toBeInTheDocument();
    expect(within(texto).getByText('Bom semestre.')).toBeInTheDocument();
    expect(within(texto).getAllByText('—').length).toBeGreaterThan(5);
  });

  it('aprova citando a versão e vai para a tela de aprovado', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/relatorios-semestrais/${ID}`]: { corpo: relatorio() },
      [`POST /api/v1/relatorios-semestrais/${ID}/aprovacao`]: {
        corpo: { relatorioId: ID, status: 'APROVADO', versaoAprovada: 1, aprovadoEm: '2026-09-21T13:00:00Z' },
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/relatorios-semestrais/${ID}`);

    await usuario.click(await screen.findByRole('button', { name: 'Aprovar relatório · versão 1' }));
    await usuario.click(screen.getByRole('button', { name: 'Sim, aprovar a versão 1' }));

    await waitFor(() =>
      expect(roteador.state.location.pathname).toBe(`/relatorios-semestrais/${ID}/aprovado`),
    );
    const envio = api.chamadas.find((c) => c.url.endsWith('/aprovacao'))!;
    expect(JSON.parse(envio.init.body as string)).toEqual({ versaoConfirmada: 1 });
  });

  it('alteração por texto envia a versão base e mostra a nova versão', async () => {
    let versao = 1;
    simularApi({
      ...BASE,
      [`GET /api/v1/relatorios-semestrais/${ID}`]: () => ({ corpo: relatorio({ versaoVigente: versao }) }),
      [`GET /api/v1/relatorios-semestrais/${ID}/versoes/2/pdf`]: { corpo: new Blob(['%PDF-']) },
      [`POST /api/v1/relatorios-semestrais/${ID}/alteracoes`]: (_url, init) => {
        expect(JSON.parse(init.body as string)).toEqual({ texto: 'Inclua o recital.', versaoBase: 1 });
        versao = 2;
        return { status: 202, corpo: { trabalhoId: 't9' } };
      },
      'GET /api/v1/trabalhos/t9': {
        corpo: {
          trabalhoId: 't9',
          tipo: 'ALTERACAO',
          status: 'CONCLUIDO',
          etapa: 'PRONTO',
          demorando: false,
          resultadoCodigo: 'NOVA_VERSAO',
          relatorioTipo: 'SEMESTRAL',
          relatorioId: ID,
          textoEntendido: 'Inclua o recital.',
        },
      },
    });
    const usuario = userEvent.setup();
    renderizarRotas(rotas, `/relatorios-semestrais/${ID}`);

    await usuario.type(await screen.findByLabelText(/Precisa ajustar algo\?/), 'Inclua o recital.');
    await usuario.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    expect(await screen.findByText('Alteração aplicada. Confira a versão 2.')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Aprovar relatório · versão 2' })).toBeInTheDocument();
  });

  it('semestral aprovado mostra a variação do 09', async () => {
    simularApi({
      ...BASE,
      [`GET /api/v1/relatorios-semestrais/${ID}`]: {
        corpo: relatorio({ status: 'APROVADO', aprovadoEm: '2026-09-21T21:42:00Z' }),
      },
    });
    renderizarRotas(rotas, `/relatorios-semestrais/${ID}/aprovado`);

    expect(await screen.findByText(/Relatório semestral · versão 1/)).toBeInTheDocument();
    expect(
      screen.getByText('Relatório aprovado em 21/09/2026 às 18:42. Ele já consta no histórico do aluno.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Matteo Mafra')).toBeInTheDocument();
  });
});
