import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rotas } from '../../src/app/rotas';
import { SESSAO_CLARA, renderizarRotas, simularApi } from '../utilitarios';

/**
 * Resposta às perguntas e pedido de alteração por áudio na Web (FR-024a, FR-027a): envio multipart
 * com o campo de áudio, etapas do processamento, texto entendido junto da nova versão, falha de
 * transcrição que mantém a versão, e o envio por arquivo ou texto quando não há gravação (o jsdom
 * não tem MediaRecorder, que é justamente o cenário de fallback).
 */

const ALUNO_ID = '0190a000-0000-7000-8000-00000000000a';
const AULA = '0190a000-0000-7000-8000-0000000000r1';
const SEMESTRAL = '0190a000-0000-7000-8000-0000000000s1';

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
};

function relatorioAula(sobrescrever: Record<string, unknown> = {}) {
  return {
    relatorioId: AULA,
    alunoId: ALUNO_ID,
    alunoNome: 'Matteo Mafra',
    professorNome: 'Clara Souza',
    dataAula: '2026-09-21',
    status: 'AGUARDANDO_CONFERENCIA',
    etapa: null,
    trabalhoEmCursoId: null,
    versaoVigente: 1,
    canalOrigem: 'WEB',
    conteudo: {
      conteudosTrabalhados: ['Estudo nº 5 de Czerny'],
      evolucao: 'Mais segurança na mão direita.',
      dificuldades: [],
      atividadesPropostas: ['Estudar os compassos 9 a 12'],
      observacoes: '',
    },
    perguntasPendentes: [],
    rodadasPerguntas: 1,
    ultimaInteracao: null,
    pdfDisponivel: true,
    aprovadoEm: null,
    atualizadoEm: '2026-09-21T13:00:00Z',
    ...sobrescrever,
  };
}

function relatorioSemestral(sobrescrever: Record<string, unknown> = {}) {
  return {
    relatorioId: SEMESTRAL,
    alunoId: ALUNO_ID,
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
    quantidadeRelatoriosEmAberto: 0,
    ultimaInteracao: null,
    pdfDisponivel: true,
    aprovadoEm: null,
    atualizadoEm: '2026-09-21T13:00:00Z',
    ...sobrescrever,
  };
}

function trabalho(sobrescrever: Record<string, unknown> = {}) {
  return {
    trabalhoId: 't7',
    tipo: 'RESPOSTA_PERGUNTAS',
    status: 'EM_EXECUCAO',
    etapa: 'TRANSCREVENDO_PEDIDO',
    demorando: false,
    resultadoCodigo: null,
    relatorioTipo: 'AULA',
    relatorioId: AULA,
    textoEntendido: null,
    ...sobrescrever,
  };
}

const arquivoDeAudio = () => new File(['audio'], 'pedido.m4a', { type: 'audio/mp4' });

const TEXTO_FALHA_ALTERACAO =
  'Não foi possível aplicar a alteração agora. O relatório continua na versão 1. Tente de novo.';

describe('TelaPerguntas — resposta por áudio (FR-024a)', () => {
  const perguntando = relatorioAula({
    status: 'AGUARDANDO_RESPOSTAS',
    perguntasPendentes: ['O aluno estudou em casa?'],
  });

  it('envia o áudio no campo multipart e mostra a etapa "Transcrevendo o pedido"', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/relatorios-aula/${AULA}`]: { corpo: perguntando },
      [`POST /api/v1/relatorios-aula/${AULA}/respostas`]: { status: 202, corpo: { trabalhoId: 't7' } },
      'GET /api/v1/trabalhos/t7': { corpo: trabalho() },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/relatorios-aula/${AULA}/perguntas`);

    await usuario.upload(await screen.findByLabelText('Arquivo de áudio'), arquivoDeAudio());
    await usuario.click(screen.getByRole('button', { name: 'Enviar resposta' }));

    await waitFor(() => expect(roteador.state.location.pathname).toBe('/trabalhos/t7'));
    const envio = api.chamadas.find((c) => c.metodo === 'POST')!;
    expect(envio.init.body).toBeInstanceOf(FormData);
    expect((envio.init.body as FormData).get('audio')).toBeInstanceOf(Blob);
    expect((envio.init.body as FormData).get('texto')).toBeNull();
    const etapas = await screen.findByRole('list', { name: 'Etapas do processamento' });
    expect(within(etapas).getByText('Transcrevendo o pedido').closest('li')).toHaveAttribute(
      'aria-current',
      'step',
    );
  });

  it('transcrição que falha mantém o relatório aguardando respostas e avisa', async () => {
    simularApi({
      ...BASE,
      [`GET /api/v1/relatorios-aula/${AULA}`]: { corpo: perguntando },
      [`POST /api/v1/relatorios-aula/${AULA}/respostas`]: { status: 202, corpo: { trabalhoId: 't7' } },
      'GET /api/v1/trabalhos/t7': {
        corpo: trabalho({ status: 'FALHOU', etapa: 'TRANSCREVENDO_PEDIDO', resultadoCodigo: 'FALHA_RESPOSTA' }),
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/relatorios-aula/${AULA}/perguntas`);

    await usuario.upload(await screen.findByLabelText('Arquivo de áudio'), arquivoDeAudio());
    await usuario.click(screen.getByRole('button', { name: 'Enviar resposta' }));

    expect(
      await screen.findByText(
        'Não conseguimos entender a resposta enviada. O relatório continua aguardando respostas. Grave de novo ou digite.',
      ),
    ).toBeInTheDocument();
    expect(roteador.state.location.pathname).toBe(`/relatorios-aula/${AULA}/perguntas`);
    expect(screen.getByText('O aluno estudou em casa?')).toBeInTheDocument();
  });

  it('sem gravação no navegador avisa e deixa responder por arquivo ou por texto', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/relatorios-aula/${AULA}`]: { corpo: perguntando },
      [`POST /api/v1/relatorios-aula/${AULA}/respostas`]: { status: 202, corpo: { trabalhoId: 't8' } },
      'GET /api/v1/trabalhos/t8': { corpo: trabalho({ trabalhoId: 't8', etapa: 'INCORPORANDO_RESPOSTAS' }) },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/relatorios-aula/${AULA}/perguntas`);

    expect(
      await screen.findByText(/Gravação indisponível neste navegador ou microfone bloqueado/),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Arquivo de áudio')).toBeInTheDocument();

    await usuario.type(screen.getByLabelText('Sua resposta'), 'Estudou três vezes.');
    await usuario.click(screen.getByRole('button', { name: 'Enviar resposta' }));

    await waitFor(() => expect(roteador.state.location.pathname).toBe('/trabalhos/t8'));
    const envio = api.chamadas.find((c) => c.metodo === 'POST')!;
    expect(JSON.parse(envio.init.body as string)).toEqual({ texto: 'Estudou três vezes.' });
  });
});

describe('TelaConferencia — pedido de alteração por áudio (FR-027a)', () => {
  it('envia o áudio com a versão base, mostra "Aplicando a alteração…" e depois o texto entendido com a nova versão', async () => {
    let versao = 1;
    let consultas = 0;
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/relatorios-aula/${AULA}`]: () => ({ corpo: relatorioAula({ versaoVigente: versao }) }),
      [`GET /api/v1/relatorios-aula/${AULA}/versoes/1/pdf`]: { corpo: new Blob(['%PDF-']) },
      [`GET /api/v1/relatorios-aula/${AULA}/versoes/2/pdf`]: { corpo: new Blob(['%PDF-']) },
      [`POST /api/v1/relatorios-aula/${AULA}/alteracoes`]: () => ({ status: 202, corpo: { trabalhoId: 't9' } }),
      'GET /api/v1/trabalhos/t9': () => {
        consultas += 1;
        if (consultas === 1) {
          return { corpo: trabalho({ trabalhoId: 't9', tipo: 'ALTERACAO' }) };
        }
        versao = 2;
        return {
          corpo: trabalho({
            trabalhoId: 't9',
            tipo: 'ALTERACAO',
            status: 'CONCLUIDO',
            etapa: 'PRONTO',
            resultadoCodigo: 'NOVA_VERSAO',
            textoEntendido: 'Diga que ele leu a partitura sem ajuda.',
          }),
        };
      },
    });
    const usuario = userEvent.setup();
    renderizarRotas(rotas, `/relatorios-aula/${AULA}`);

    await usuario.upload(await screen.findByLabelText('Arquivo de áudio'), arquivoDeAudio());
    await usuario.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    expect(await screen.findByText('Aplicando a alteração…')).toBeInTheDocument();
    const envio = api.chamadas.find((c) => c.metodo === 'POST')!;
    expect(envio.init.body).toBeInstanceOf(FormData);
    expect((envio.init.body as FormData).get('audio')).toBeInstanceOf(Blob);
    expect((envio.init.body as FormData).get('versaoBase')).toBe('1');

    expect(
      await screen.findByText('Alteração aplicada. Confira a versão 2.', {}, { timeout: 8000 }),
    ).toBeInTheDocument();
    expect(screen.getByText('Entendi: “Diga que ele leu a partitura sem ajuda.”')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Aprovar relatório · versão 2' })).toBeInTheDocument();
  }, 15000);

  it('transcrição do pedido que falha mantém a versão e avisa', async () => {
    simularApi({
      ...BASE,
      [`GET /api/v1/relatorios-aula/${AULA}`]: { corpo: relatorioAula() },
      [`GET /api/v1/relatorios-aula/${AULA}/versoes/1/pdf`]: { corpo: new Blob(['%PDF-']) },
      [`POST /api/v1/relatorios-aula/${AULA}/alteracoes`]: { status: 202, corpo: { trabalhoId: 't9' } },
      'GET /api/v1/trabalhos/t9': {
        corpo: trabalho({
          trabalhoId: 't9',
          tipo: 'ALTERACAO',
          status: 'FALHOU',
          etapa: 'TRANSCREVENDO_PEDIDO',
          resultadoCodigo: 'FALHA_ALTERACAO',
        }),
      },
    });
    const usuario = userEvent.setup();
    renderizarRotas(rotas, `/relatorios-aula/${AULA}`);

    await usuario.upload(await screen.findByLabelText('Arquivo de áudio'), arquivoDeAudio());
    await usuario.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    expect(await screen.findByText(TEXTO_FALHA_ALTERACAO, {}, { timeout: 8000 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aprovar relatório · versão 1' })).toBeInTheDocument();
  }, 15000);

  it('sem gravação no navegador o pedido segue por texto', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/relatorios-aula/${AULA}`]: { corpo: relatorioAula() },
      [`GET /api/v1/relatorios-aula/${AULA}/versoes/1/pdf`]: { corpo: new Blob(['%PDF-']) },
      [`POST /api/v1/relatorios-aula/${AULA}/alteracoes`]: { status: 202, corpo: { trabalhoId: 't9' } },
      'GET /api/v1/trabalhos/t9': { corpo: trabalho({ trabalhoId: 't9', tipo: 'ALTERACAO' }) },
    });
    const usuario = userEvent.setup();
    renderizarRotas(rotas, `/relatorios-aula/${AULA}`);

    expect(
      await screen.findByText(/Gravação indisponível neste navegador ou microfone bloqueado/),
    ).toBeInTheDocument();
    await usuario.type(await screen.findByLabelText(/Precisa ajustar algo\?/), 'Inclua o recital.');
    await usuario.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    await waitFor(() => expect(api.chamadas.some((c) => c.metodo === 'POST')).toBe(true));
    const envio = api.chamadas.find((c) => c.metodo === 'POST')!;
    expect(JSON.parse(envio.init.body as string)).toEqual({ texto: 'Inclua o recital.', versaoBase: 1 });
  });
});

describe('TelaConferenciaSemestral — pedido de alteração por áudio (FR-027a)', () => {
  const BASE_SEMESTRAL = {
    ...BASE,
    [`GET /api/v1/relatorios-semestrais/${SEMESTRAL}/versoes/1/pdf`]: { corpo: new Blob(['%PDF-']) },
    [`GET /api/v1/relatorios-semestrais/${SEMESTRAL}/versoes/2/pdf`]: { corpo: new Blob(['%PDF-']) },
  };

  it('envia o áudio com a versão base e mostra o texto entendido com a nova versão', async () => {
    let versao = 1;
    const api = simularApi({
      ...BASE_SEMESTRAL,
      [`GET /api/v1/relatorios-semestrais/${SEMESTRAL}`]: () => ({
        corpo: relatorioSemestral({ versaoVigente: versao }),
      }),
      [`POST /api/v1/relatorios-semestrais/${SEMESTRAL}/alteracoes`]: () => {
        versao = 2;
        return { status: 202, corpo: { trabalhoId: 't11' } };
      },
      'GET /api/v1/trabalhos/t11': {
        corpo: trabalho({
          trabalhoId: 't11',
          tipo: 'ALTERACAO',
          status: 'CONCLUIDO',
          etapa: 'PRONTO',
          resultadoCodigo: 'NOVA_VERSAO',
          relatorioTipo: 'SEMESTRAL',
          relatorioId: SEMESTRAL,
          textoEntendido: 'Parecer: excelente semestre.',
        }),
      },
    });
    const usuario = userEvent.setup();
    renderizarRotas(rotas, `/relatorios-semestrais/${SEMESTRAL}`);

    await usuario.upload(await screen.findByLabelText('Arquivo de áudio'), arquivoDeAudio());
    await usuario.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    expect(await screen.findByText('Alteração aplicada. Confira a versão 2.')).toBeInTheDocument();
    expect(screen.getByText('Entendi: “Parecer: excelente semestre.”')).toBeInTheDocument();
    const envio = api.chamadas.find((c) => c.metodo === 'POST')!;
    expect(envio.init.body).toBeInstanceOf(FormData);
    expect((envio.init.body as FormData).get('audio')).toBeInstanceOf(Blob);
    expect((envio.init.body as FormData).get('versaoBase')).toBe('1');
  });

  it('transcrição que falha mantém a versão', async () => {
    simularApi({
      ...BASE_SEMESTRAL,
      [`GET /api/v1/relatorios-semestrais/${SEMESTRAL}`]: { corpo: relatorioSemestral() },
      [`POST /api/v1/relatorios-semestrais/${SEMESTRAL}/alteracoes`]: {
        status: 202,
        corpo: { trabalhoId: 't11' },
      },
      'GET /api/v1/trabalhos/t11': {
        corpo: trabalho({
          trabalhoId: 't11',
          tipo: 'ALTERACAO',
          status: 'FALHOU',
          etapa: 'TRANSCREVENDO_PEDIDO',
          resultadoCodigo: 'FALHA_ALTERACAO',
          relatorioTipo: 'SEMESTRAL',
          relatorioId: SEMESTRAL,
        }),
      },
    });
    const usuario = userEvent.setup();
    renderizarRotas(rotas, `/relatorios-semestrais/${SEMESTRAL}`);

    await usuario.upload(await screen.findByLabelText('Arquivo de áudio'), arquivoDeAudio());
    await usuario.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    expect(await screen.findByText(TEXTO_FALHA_ALTERACAO)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aprovar relatório · versão 1' })).toBeInTheDocument();
  });
});
