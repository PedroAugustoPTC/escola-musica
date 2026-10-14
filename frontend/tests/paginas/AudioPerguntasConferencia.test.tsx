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
  'GET /api/v1/session': { corpo: SESSAO_CLARA },
  'GET /api/v1/home': {
    corpo: {
      teacher: SESSAO_CLARA.teacher,
      totalStudents: 1,
      students: [],
      openReports: [],
      audioLimitMb: 50,
    },
  },
};

function relatorioAula(sobrescrever: Record<string, unknown> = {}) {
  return {
    reportId: AULA,
    studentId: ALUNO_ID,
    studentName: 'Matteo Mafra',
    teacherName: 'Clara Souza',
    lessonDate: '2026-09-21',
    status: 'AWAITING_REVIEW',
    step: null,
    activeJobId: null,
    currentVersion: 1,
    originChannel: 'WEB',
    content: {
      workedTopics: ['Estudo nº 5 de Czerny'],
      progress: 'Mais segurança na mão direita.',
      difficulties: [],
      proposedActivities: ['Estudar os compassos 9 a 12'],
      observations: '',
    },
    pendingQuestions: [],
    questionRounds: 1,
    lastInteraction: null,
    pdfAvailable: true,
    approvedAt: null,
    updatedAt: '2026-09-21T13:00:00Z',
    ...sobrescrever,
  };
}

function relatorioSemestral(sobrescrever: Record<string, unknown> = {}) {
  return {
    reportId: SEMESTRAL,
    studentId: ALUNO_ID,
    periodKey: '2026-1',
    status: 'AWAITING_REVIEW',
    step: null,
    activeJobId: null,
    currentVersion: 1,
    originChannel: 'WEB',
    generalInformation: {
      name: 'Matteo Mafra',
      age: 14,
      instrument: 'Piano',
      teacher: 'Clara Souza',
      periodLabel: '1º semestre 2026',
      periodStart: '2026-01-01',
      periodEnd: '2026-06-30',
      consideredLessonCount: 12,
    },
    content: {
      attendanceAndStudy: {
        frequenciaAulas: 'Regular',
        regularidadeEstudoEmCasa: '',
        engajamento: '',
        comentarioGeral: '',
      },
      technique: {
        workedTopics: ['Escalas'],
        evolucaoTecnica: '',
        pontosADesenvolver: [],
        estudosRealizados: [],
      },
      musicality: {},
      readingAndMemorization: {},
      attentionPoints: { items: [] },
      pedagogicalStrategies: { items: [] },
      familySupport: {},
      nextSemesterPlan: {},
      finalOpinion: 'Bom semestre.',
    },
    openReportCount: 0,
    lastInteraction: null,
    pdfAvailable: true,
    approvedAt: null,
    updatedAt: '2026-09-21T13:00:00Z',
    ...sobrescrever,
  };
}

function trabalho(sobrescrever: Record<string, unknown> = {}) {
  return {
    jobId: 't7',
    type: 'QUESTION_ANSWER',
    status: 'RUNNING',
    step: 'TRANSCRIBING_REQUEST',
    delayed: false,
    resultCode: null,
    reportType: 'LESSON',
    reportId: AULA,
    understoodText: null,
    ...sobrescrever,
  };
}

const arquivoDeAudio = () => new File(['audio'], 'pedido.m4a', { type: 'audio/mp4' });

const TEXTO_FALHA_ALTERACAO =
  'Não foi possível aplicar a alteração agora. O relatório continua na versão 1. Tente de novo.';

describe('TelaPerguntas — resposta por áudio (FR-024a)', () => {
  const perguntando = relatorioAula({
    status: 'AWAITING_ANSWERS',
    pendingQuestions: ['O aluno estudou em casa?'],
  });

  it('envia o áudio no campo multipart e mostra a etapa "Transcrevendo o pedido"', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${AULA}`]: { corpo: perguntando },
      [`POST /api/v1/lesson-reports/${AULA}/answers`]: { status: 202, corpo: { jobId: 't7' } },
      'GET /api/v1/jobs/t7': { corpo: trabalho() },
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
      [`GET /api/v1/lesson-reports/${AULA}`]: { corpo: perguntando },
      [`POST /api/v1/lesson-reports/${AULA}/answers`]: { status: 202, corpo: { jobId: 't7' } },
      'GET /api/v1/jobs/t7': {
        corpo: trabalho({ status: 'FAILED', step: 'TRANSCRIBING_REQUEST', resultCode: 'ANSWER_FAILED' }),
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
      [`GET /api/v1/lesson-reports/${AULA}`]: { corpo: perguntando },
      [`POST /api/v1/lesson-reports/${AULA}/answers`]: { status: 202, corpo: { jobId: 't8' } },
      'GET /api/v1/jobs/t8': { corpo: trabalho({ jobId: 't8', step: 'INCORPORATING_ANSWERS' }) },
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
    expect(JSON.parse(envio.init.body as string)).toEqual({ text: 'Estudou três vezes.' });
  });
});

describe('TelaConferencia — pedido de alteração por áudio (FR-027a)', () => {
  it('envia o áudio com a versão base, mostra "Aplicando a alteração…" e depois o texto entendido com a nova versão', async () => {
    let versao = 1;
    let consultas = 0;
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${AULA}`]: () => ({ corpo: relatorioAula({ currentVersion: versao }) }),
      [`GET /api/v1/lesson-reports/${AULA}/versions/1/pdf`]: { corpo: new Blob(['%PDF-']) },
      [`GET /api/v1/lesson-reports/${AULA}/versions/2/pdf`]: { corpo: new Blob(['%PDF-']) },
      [`POST /api/v1/lesson-reports/${AULA}/changes`]: () => ({ status: 202, corpo: { jobId: 't9' } }),
      'GET /api/v1/jobs/t9': () => {
        consultas += 1;
        if (consultas === 1) {
          return { corpo: trabalho({ jobId: 't9', type: 'CHANGE' }) };
        }
        versao = 2;
        return {
          corpo: trabalho({
            jobId: 't9',
            type: 'CHANGE',
            status: 'COMPLETED',
            step: 'READY',
            resultCode: 'NEW_VERSION',
            understoodText: 'Diga que ele leu a partitura sem ajuda.',
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
    expect((envio.init.body as FormData).get('baseVersion')).toBe('1');

    expect(
      await screen.findByText('Alteração aplicada. Confira a versão 2.', {}, { timeout: 8000 }),
    ).toBeInTheDocument();
    expect(screen.getByText('Entendi: “Diga que ele leu a partitura sem ajuda.”')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Aprovar relatório · versão 2' })).toBeInTheDocument();
  }, 15000);

  it('transcrição do pedido que falha mantém a versão e avisa', async () => {
    simularApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${AULA}`]: { corpo: relatorioAula() },
      [`GET /api/v1/lesson-reports/${AULA}/versions/1/pdf`]: { corpo: new Blob(['%PDF-']) },
      [`POST /api/v1/lesson-reports/${AULA}/changes`]: { status: 202, corpo: { jobId: 't9' } },
      'GET /api/v1/jobs/t9': {
        corpo: trabalho({
          jobId: 't9',
          type: 'CHANGE',
          status: 'FAILED',
          step: 'TRANSCRIBING_REQUEST',
          resultCode: 'CHANGE_FAILED',
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
      [`GET /api/v1/lesson-reports/${AULA}`]: { corpo: relatorioAula() },
      [`GET /api/v1/lesson-reports/${AULA}/versions/1/pdf`]: { corpo: new Blob(['%PDF-']) },
      [`POST /api/v1/lesson-reports/${AULA}/changes`]: { status: 202, corpo: { jobId: 't9' } },
      'GET /api/v1/jobs/t9': { corpo: trabalho({ jobId: 't9', type: 'CHANGE' }) },
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
    expect(JSON.parse(envio.init.body as string)).toEqual({ text: 'Inclua o recital.', baseVersion: 1 });
  });
});

describe('TelaConferenciaSemestral — pedido de alteração por áudio (FR-027a)', () => {
  const BASE_SEMESTRAL = {
    ...BASE,
    [`GET /api/v1/semester-reports/${SEMESTRAL}/versions/1/pdf`]: { corpo: new Blob(['%PDF-']) },
    [`GET /api/v1/semester-reports/${SEMESTRAL}/versions/2/pdf`]: { corpo: new Blob(['%PDF-']) },
  };

  it('envia o áudio com a versão base e mostra o texto entendido com a nova versão', async () => {
    let versao = 1;
    const api = simularApi({
      ...BASE_SEMESTRAL,
      [`GET /api/v1/semester-reports/${SEMESTRAL}`]: () => ({
        corpo: relatorioSemestral({ currentVersion: versao }),
      }),
      [`POST /api/v1/semester-reports/${SEMESTRAL}/changes`]: () => {
        versao = 2;
        return { status: 202, corpo: { jobId: 't11' } };
      },
      'GET /api/v1/jobs/t11': {
        corpo: trabalho({
          jobId: 't11',
          type: 'CHANGE',
          status: 'COMPLETED',
          step: 'READY',
          resultCode: 'NEW_VERSION',
          reportType: 'SEMESTER',
          reportId: SEMESTRAL,
          understoodText: 'Parecer: excelente semestre.',
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
    expect((envio.init.body as FormData).get('baseVersion')).toBe('1');
  });

  it('transcrição que falha mantém a versão', async () => {
    simularApi({
      ...BASE_SEMESTRAL,
      [`GET /api/v1/semester-reports/${SEMESTRAL}`]: { corpo: relatorioSemestral() },
      [`POST /api/v1/semester-reports/${SEMESTRAL}/changes`]: {
        status: 202,
        corpo: { jobId: 't11' },
      },
      'GET /api/v1/jobs/t11': {
        corpo: trabalho({
          jobId: 't11',
          type: 'CHANGE',
          status: 'FAILED',
          step: 'TRANSCRIBING_REQUEST',
          resultCode: 'CHANGE_FAILED',
          reportType: 'SEMESTER',
          reportId: SEMESTRAL,
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
