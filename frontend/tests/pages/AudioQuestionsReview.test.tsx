import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../src/app/routes';
import { LIGHT_SESSION, renderRoutes, mockApi } from '../utils';

/**
 * Resposta às perguntas e pedido de alteração por áudio na Web (FR-024a, FR-027a): envio multipart
 * com o campo de áudio, etapas do processamento, texto entendido junto da nova versão, falha de
 * transcrição que mantém a versão, e o envio por arquivo ou texto quando não há gravação (o jsdom
 * não tem MediaRecorder, que é justamente o cenário de fallback).
 */

const STUDENT_ID = '0190a000-0000-7000-8000-00000000000a';
const LESSON = '0190a000-0000-7000-8000-0000000000r1';
const SEMESTER = '0190a000-0000-7000-8000-0000000000s1';

const BASE = {
  'GET /api/v1/session': { body: LIGHT_SESSION },
  'GET /api/v1/home': {
    body: {
      teacher: LIGHT_SESSION.teacher,
      totalStudents: 1,
      students: [],
      openReports: [],
      audioLimitMb: 50,
    },
  },
};

function lessonReport(overwrite: Record<string, unknown> = {}) {
  return {
    reportId: LESSON,
    studentId: STUDENT_ID,
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
    ...overwrite,
  };
}

function semesterReport(overwrite: Record<string, unknown> = {}) {
  return {
    reportId: SEMESTER,
    studentId: STUDENT_ID,
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
        lessonAttendance: 'Regular',
        homeStudyRegularity: '',
        engagement: '',
        generalComment: '',
      },
      technique: {
        workedTopics: ['Escalas'],
        technicalProgress: '',
        pointsToDevelop: [],
        completedStudies: [],
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
    ...overwrite,
  };
}

function job(overwrite: Record<string, unknown> = {}) {
  return {
    jobId: 't7',
    type: 'QUESTION_ANSWER',
    status: 'RUNNING',
    step: 'TRANSCRIBING_REQUEST',
    delayed: false,
    resultCode: null,
    reportType: 'LESSON',
    reportId: LESSON,
    understoodText: null,
    ...overwrite,
  };
}

const audioFile = () => new File(['audio'], 'request.m4a', { type: 'audio/mp4' });

const CHANGE_FAILURE_TEXT =
  'Não foi possível aplicar a alteração agora. O relatório continua na versão 1. Tente de novo.';

describe('TelaPerguntas — resposta por áudio (FR-024a)', () => {
  const asking = lessonReport({
    status: 'AWAITING_ANSWERS',
    pendingQuestions: ['O aluno estudou em casa?'],
  });

  it('envia o áudio no campo multipart e mostra a etapa "Transcrevendo o pedido"', async () => {
    const api = mockApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${LESSON}`]: { body: asking },
      [`POST /api/v1/lesson-reports/${LESSON}/answers`]: { status: 202, body: { jobId: 't7' } },
      'GET /api/v1/jobs/t7': { body: job() },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/lesson-reports/${LESSON}/questions`);

    await user.upload(await screen.findByLabelText('Arquivo de áudio'), audioFile());
    await user.click(screen.getByRole('button', { name: 'Enviar resposta' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/jobs/t7'));
    const sending = api.calls.find((c) => c.method === 'POST')!;
    expect(sending.init.body).toBeInstanceOf(FormData);
    expect((sending.init.body as FormData).get('audio')).toBeInstanceOf(Blob);
    expect((sending.init.body as FormData).get('text')).toBeNull();
    const steps = await screen.findByRole('list', { name: 'Etapas do processamento' });
    expect(within(steps).getByText('Transcrevendo o pedido').closest('li')).toHaveAttribute(
      'aria-current',
      'step',
    );
  });

  it('transcrição que falha mantém o relatório aguardando respostas e avisa', async () => {
    mockApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${LESSON}`]: { body: asking },
      [`POST /api/v1/lesson-reports/${LESSON}/answers`]: { status: 202, body: { jobId: 't7' } },
      'GET /api/v1/jobs/t7': {
        body: job({ status: 'FAILED', step: 'TRANSCRIBING_REQUEST', resultCode: 'ANSWER_FAILED' }),
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/lesson-reports/${LESSON}/questions`);

    await user.upload(await screen.findByLabelText('Arquivo de áudio'), audioFile());
    await user.click(screen.getByRole('button', { name: 'Enviar resposta' }));

    expect(
      await screen.findByText(
        'Não conseguimos entender a resposta enviada. O relatório continua aguardando respostas. Grave de novo ou digite.',
      ),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/lesson-reports/${LESSON}/questions`);
    expect(screen.getByText('O aluno estudou em casa?')).toBeInTheDocument();
  });

  it('sem gravação no navegador avisa e deixa responder por arquivo ou por texto', async () => {
    const api = mockApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${LESSON}`]: { body: asking },
      [`POST /api/v1/lesson-reports/${LESSON}/answers`]: { status: 202, body: { jobId: 't8' } },
      'GET /api/v1/jobs/t8': { body: job({ jobId: 't8', step: 'INCORPORATING_ANSWERS' }) },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/lesson-reports/${LESSON}/questions`);

    expect(
      await screen.findByText(/Gravação indisponível neste navegador ou microfone bloqueado/),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Arquivo de áudio')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Sua resposta'), 'Estudou três vezes.');
    await user.click(screen.getByRole('button', { name: 'Enviar resposta' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/jobs/t8'));
    const sending = api.calls.find((c) => c.method === 'POST')!;
    expect(JSON.parse(sending.init.body as string)).toEqual({ text: 'Estudou três vezes.' });
  });
});

describe('TelaConferencia — pedido de alteração por áudio (FR-027a)', () => {
  it('envia o áudio com a versão base, mostra "Aplicando a alteração…" e depois o texto entendido com a nova versão', async () => {
    let version = 1;
    let queries = 0;
    const api = mockApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${LESSON}`]: () => ({ body: lessonReport({ currentVersion: version }) }),
      [`GET /api/v1/lesson-reports/${LESSON}/versions/1/pdf`]: { body: new Blob(['%PDF-']) },
      [`GET /api/v1/lesson-reports/${LESSON}/versions/2/pdf`]: { body: new Blob(['%PDF-']) },
      [`POST /api/v1/lesson-reports/${LESSON}/changes`]: () => ({ status: 202, body: { jobId: 't9' } }),
      'GET /api/v1/jobs/t9': () => {
        queries += 1;
        if (queries === 1) {
          return { body: job({ jobId: 't9', type: 'CHANGE' }) };
        }
        version = 2;
        return {
          body: job({
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
    const user = userEvent.setup();
    renderRoutes(routes, `/lesson-reports/${LESSON}`);

    await user.upload(await screen.findByLabelText('Arquivo de áudio'), audioFile());
    await user.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    expect(await screen.findByText('Aplicando a alteração…')).toBeInTheDocument();
    const sending = api.calls.find((c) => c.method === 'POST')!;
    expect(sending.init.body).toBeInstanceOf(FormData);
    expect((sending.init.body as FormData).get('audio')).toBeInstanceOf(Blob);
    expect((sending.init.body as FormData).get('baseVersion')).toBe('1');

    expect(
      await screen.findByText('Alteração aplicada. Confira a versão 2.', {}, { timeout: 8000 }),
    ).toBeInTheDocument();
    expect(screen.getByText('Entendi: “Diga que ele leu a partitura sem ajuda.”')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Aprovar relatório · versão 2' })).toBeInTheDocument();
  }, 15000);

  it('transcrição do pedido que falha mantém a versão e avisa', async () => {
    mockApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${LESSON}`]: { body: lessonReport() },
      [`GET /api/v1/lesson-reports/${LESSON}/versions/1/pdf`]: { body: new Blob(['%PDF-']) },
      [`POST /api/v1/lesson-reports/${LESSON}/changes`]: { status: 202, body: { jobId: 't9' } },
      'GET /api/v1/jobs/t9': {
        body: job({
          jobId: 't9',
          type: 'CHANGE',
          status: 'FAILED',
          step: 'TRANSCRIBING_REQUEST',
          resultCode: 'CHANGE_FAILED',
        }),
      },
    });
    const user = userEvent.setup();
    renderRoutes(routes, `/lesson-reports/${LESSON}`);

    await user.upload(await screen.findByLabelText('Arquivo de áudio'), audioFile());
    await user.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    expect(await screen.findByText(CHANGE_FAILURE_TEXT, {}, { timeout: 8000 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aprovar relatório · versão 1' })).toBeInTheDocument();
  }, 15000);

  it('sem gravação no navegador o pedido segue por texto', async () => {
    const api = mockApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${LESSON}`]: { body: lessonReport() },
      [`GET /api/v1/lesson-reports/${LESSON}/versions/1/pdf`]: { body: new Blob(['%PDF-']) },
      [`POST /api/v1/lesson-reports/${LESSON}/changes`]: { status: 202, body: { jobId: 't9' } },
      'GET /api/v1/jobs/t9': { body: job({ jobId: 't9', type: 'CHANGE' }) },
    });
    const user = userEvent.setup();
    renderRoutes(routes, `/lesson-reports/${LESSON}`);

    expect(
      await screen.findByText(/Gravação indisponível neste navegador ou microfone bloqueado/),
    ).toBeInTheDocument();
    await user.type(await screen.findByLabelText(/Precisa ajustar algo\?/), 'Inclua o recital.');
    await user.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    await waitFor(() => expect(api.calls.some((c) => c.method === 'POST')).toBe(true));
    const sending = api.calls.find((c) => c.method === 'POST')!;
    expect(JSON.parse(sending.init.body as string)).toEqual({ text: 'Inclua o recital.', baseVersion: 1 });
  });
});

describe('TelaConferenciaSemestral — pedido de alteração por áudio (FR-027a)', () => {
  const SEMESTER_BASE = {
    ...BASE,
    [`GET /api/v1/semester-reports/${SEMESTER}/versions/1/pdf`]: { body: new Blob(['%PDF-']) },
    [`GET /api/v1/semester-reports/${SEMESTER}/versions/2/pdf`]: { body: new Blob(['%PDF-']) },
  };

  it('envia o áudio com a versão base e mostra o texto entendido com a nova versão', async () => {
    let version = 1;
    const api = mockApi({
      ...SEMESTER_BASE,
      [`GET /api/v1/semester-reports/${SEMESTER}`]: () => ({
        body: semesterReport({ currentVersion: version }),
      }),
      [`POST /api/v1/semester-reports/${SEMESTER}/changes`]: () => {
        version = 2;
        return { status: 202, body: { jobId: 't11' } };
      },
      'GET /api/v1/jobs/t11': {
        body: job({
          jobId: 't11',
          type: 'CHANGE',
          status: 'COMPLETED',
          step: 'READY',
          resultCode: 'NEW_VERSION',
          reportType: 'SEMESTER',
          reportId: SEMESTER,
          understoodText: 'Parecer: excelente semestre.',
        }),
      },
    });
    const user = userEvent.setup();
    renderRoutes(routes, `/semester-reports/${SEMESTER}`);

    await user.upload(await screen.findByLabelText('Arquivo de áudio'), audioFile());
    await user.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    expect(await screen.findByText('Alteração aplicada. Confira a versão 2.')).toBeInTheDocument();
    expect(screen.getByText('Entendi: “Parecer: excelente semestre.”')).toBeInTheDocument();
    const sending = api.calls.find((c) => c.method === 'POST')!;
    expect(sending.init.body).toBeInstanceOf(FormData);
    expect((sending.init.body as FormData).get('audio')).toBeInstanceOf(Blob);
    expect((sending.init.body as FormData).get('baseVersion')).toBe('1');
  });

  it('transcrição que falha mantém a versão', async () => {
    mockApi({
      ...SEMESTER_BASE,
      [`GET /api/v1/semester-reports/${SEMESTER}`]: { body: semesterReport() },
      [`POST /api/v1/semester-reports/${SEMESTER}/changes`]: {
        status: 202,
        body: { jobId: 't11' },
      },
      'GET /api/v1/jobs/t11': {
        body: job({
          jobId: 't11',
          type: 'CHANGE',
          status: 'FAILED',
          step: 'TRANSCRIBING_REQUEST',
          resultCode: 'CHANGE_FAILED',
          reportType: 'SEMESTER',
          reportId: SEMESTER,
        }),
      },
    });
    const user = userEvent.setup();
    renderRoutes(routes, `/semester-reports/${SEMESTER}`);

    await user.upload(await screen.findByLabelText('Arquivo de áudio'), audioFile());
    await user.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    expect(await screen.findByText(CHANGE_FAILURE_TEXT)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aprovar relatório · versão 1' })).toBeInTheDocument();
  });
});
