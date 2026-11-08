import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../src/app/routes';
import { LIGHT_SESSION, renderRoutes, mockApi } from '../utils';

const STUDENT = {
  id: '0190a000-0000-7000-8000-00000000000a',
  name: 'Matteo Mafra',
  instrument: 'Piano',
  lastLessonAt: null,
  hasOpenReport: false,
};

const REPORT = '0190a000-0000-7000-8000-0000000000r1';

const HOME = {
  teacher: LIGHT_SESSION.teacher,
  totalStudents: 1,
  students: [STUDENT],
  openReports: [],
  audioLimitMb: 50,
};

function report(overwrite: Record<string, unknown> = {}) {
  return {
    reportId: REPORT,
    studentId: STUDENT.id,
    studentName: STUDENT.name,
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

function job(overwrite: Record<string, unknown> = {}) {
  return {
    jobId: 't1',
    type: 'LESSON_REGISTRATION',
    status: 'RUNNING',
    step: 'TRANSCRIBING',
    delayed: false,
    resultCode: null,
    reportType: null,
    reportId: null,
    understoodText: null,
    ...overwrite,
  };
}

const BASE = {
  'GET /api/v1/session': { body: LIGHT_SESSION },
  'GET /api/v1/home': { body: HOME },
};

describe('TelaGravacao (05)', () => {
  it('envio só com áudio; envia o arquivo e segue para o processamento', async () => {
    const api = mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT.id}`]: { body: STUDENT },
      [`POST /api/v1/students/${STUDENT.id}/lesson-reports`]: { status: 202, body: { jobId: 't1' } },
      'GET /api/v1/jobs/t1': { body: job() },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/students/${STUDENT.id}/new-lesson`);

    expect(await screen.findByRole('heading', { name: 'Matteo Mafra' })).toBeInTheDocument();
    const send = screen.getByRole('button', { name: 'Enviar áudio e gerar relatório' });
    expect(send).toBeDisabled();
    expect(screen.getAllByText('MP3, M4A, OGG, WAV, WebM ou vídeo MP4 · até 50 MB').length).toBeGreaterThan(
      0,
    );

    await user.upload(
      screen.getByLabelText('Arquivo de áudio'),
      new File(['a'], 'lesson.m4a', { type: 'audio/mp4' }),
    );
    await user.click(send);

    await waitFor(() => expect(router.state.location.pathname).toBe('/jobs/t1'));
    const sending = api.calls.find((c) => c.method === 'POST')!;
    expect(sending.init.body).toBeInstanceOf(FormData);
    expect((sending.init.body as FormData).get('audio')).toBeInstanceOf(Blob);
  });

  it('bloqueio por falta de consentimento avisa sem enviar e oferece registrar o consentimento', async () => {
    mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT.id}`]: { body: STUDENT },
      [`POST /api/v1/students/${STUDENT.id}/lesson-reports`]: {
        status: 409,
        body: { code: 'CONSENT_REQUIRED', message: 'x' },
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/students/${STUDENT.id}/new-lesson`);

    await screen.findByRole('heading', { name: 'Matteo Mafra' });
    await user.upload(
      screen.getByLabelText('Arquivo de áudio'),
      new File(['a'], 'lesson.m4a', { type: 'audio/mp4' }),
    );
    await user.click(screen.getByRole('button', { name: 'Enviar áudio e gerar relatório' }));

    const link = await screen.findByRole('link', { name: 'Registrar consentimento' });
    expect(link.closest('[role="alert"]')).not.toBeNull();
    expect(link).toHaveAttribute('href', `/my-students/${STUDENT.id}`);
    expect(router.state.location.pathname).toBe(`/students/${STUDENT.id}/new-lesson`);
  });

  it('relatório em aberto (409) leva à tela 04', async () => {
    mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT.id}`]: { body: STUDENT },
      [`POST /api/v1/students/${STUDENT.id}/lesson-reports`]: {
        status: 409,
        body: { code: 'REPORT_ALREADY_OPEN', message: 'Você tem um relatório em aberto.' },
      },
      [`GET /api/v1/students/${STUDENT.id}/open-report`]: {
        body: {
          type: 'LESSON',
          reportId: REPORT,
          jobId: null,
          studentId: STUDENT.id,
          studentName: STUDENT.name,
          status: 'AWAITING_REVIEW',
          step: null,
          periodKey: null,
          originChannel: 'TELEGRAM',
          updatedAt: '2026-09-20T12:57:00Z',
        },
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/students/${STUDENT.id}/new-lesson`);

    await user.upload(
      await screen.findByLabelText('Arquivo de áudio'),
      new File(['a'], 'lesson.m4a', { type: 'audio/mp4' }),
    );
    await user.click(screen.getByRole('button', { name: 'Enviar áudio e gerar relatório' }));

    expect(
      await screen.findByRole('heading', { name: 'Você tem um relatório em aberto' }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/students/${STUDENT.id}/open-report`);
  });
});

describe('TelaProcessando (06)', () => {
  it('mostra a etapa atual e, ao concluir com perguntas, vai para a tela de perguntas', async () => {
    let queries = 0;
    mockApi({
      ...BASE,
      'GET /api/v1/jobs/t1': () => {
        queries += 1;
        return {
          body:
            queries === 1
              ? job()
              : job({
                  status: 'COMPLETED',
                  step: 'READY',
                  resultCode: 'PENDING_QUESTIONS',
                  reportType: 'LESSON',
                  reportId: REPORT,
                }),
        };
      },
      [`GET /api/v1/lesson-reports/${REPORT}`]: {
        body: report({ status: 'AWAITING_ANSWERS', pendingQuestions: ['Estudou em casa?'] }),
      },
    });
    const { router } = renderRoutes(routes, '/jobs/t1');

    expect(await screen.findByRole('heading', { name: 'Transcrevendo…' })).toBeInTheDocument();
    const steps = screen.getByRole('list', { name: 'Etapas do processamento' });
    expect(within(steps).getByText('Transcrevendo').closest('li')).toHaveAttribute('aria-current', 'step');

    await waitFor(() => expect(router.state.location.pathname).toBe(`/lesson-reports/${REPORT}/questions`), {
      timeout: 5000,
    });
  });

  it('avisa quando está demorando', async () => {
    mockApi({ ...BASE, 'GET /api/v1/jobs/t1': { body: job({ delayed: true }) } });
    renderRoutes(routes, '/jobs/t1');

    expect(await screen.findByText(/Está demorando mais que o normal/)).toBeInTheDocument();
  });

  it('falha na estruturação oferece tentar de novo sem reenviar o áudio', async () => {
    const api = mockApi({
      ...BASE,
      'GET /api/v1/jobs/t1': {
        body: job({
          status: 'FAILED',
          step: 'STRUCTURING',
          resultCode: 'STRUCTURING_FAILED',
          reportType: 'LESSON',
          reportId: REPORT,
        }),
      },
      [`POST /api/v1/lesson-reports/${REPORT}/retry`]: {
        status: 202,
        body: { jobId: 't2' },
      },
      'GET /api/v1/jobs/t2': {
        body: job({ jobId: 't2', type: 'RETRY', step: 'STRUCTURING' }),
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, '/jobs/t1');

    expect(
      await screen.findByRole('heading', { name: 'Não foi possível processar o áudio.' }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/jobs/t2'));
    expect(api.calls.some((c) => c.url.endsWith('/retry'))).toBe(true);
  });
});

describe('TelaPerguntas (07)', () => {
  it('lista as perguntas numeradas, exige resposta e envia o texto', async () => {
    const api = mockApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${REPORT}`]: {
        body: report({
          status: 'AWAITING_ANSWERS',
          currentVersion: 0,
          pendingQuestions: ['O aluno estudou em casa?', 'Qual o repertório da próxima aula?'],
        }),
      },
      [`POST /api/v1/lesson-reports/${REPORT}/answers`]: { status: 202, body: { jobId: 't3' } },
      'GET /api/v1/jobs/t3': {
        body: job({ jobId: 't3', type: 'QUESTION_ANSWER', step: 'INCORPORATING_ANSWERS' }),
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/lesson-reports/${REPORT}/questions`);

    expect(await screen.findByText('O aluno estudou em casa?')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Enviar resposta' }));
    expect(screen.getByText('Escreva ou grave a sua resposta antes de enviar.')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Sua resposta'), 'Estudou sim; vai preparar a valsa.');
    await user.click(screen.getByRole('button', { name: 'Enviar resposta' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/jobs/t3'));
    const sending = api.calls.find((c) => c.method === 'POST' && c.url.endsWith('/answers'))!;
    expect(JSON.parse(sending.init.body as string)).toEqual({ text: 'Estudou sim; vai preparar a valsa.' });
  });
});

describe('TelaConferencia (08)', () => {
  const review = {
    ...BASE,
    [`GET /api/v1/lesson-reports/${REPORT}`]: { body: report() },
    [`GET /api/v1/lesson-reports/${REPORT}/versions/1/pdf`]: {
      body: new Blob(['%PDF-'], { type: 'application/pdf' }),
    },
  };

  it('no celular mostra as abas Texto e Ver PDF; seções vazias aparecem como —', async () => {
    mockApi(review);
    const user = userEvent.setup();
    renderRoutes(routes, `/lesson-reports/${REPORT}`);

    expect(
      await screen.findByRole('heading', { name: 'Confira o relatório antes de aprovar' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Versão 1')).toBeInTheDocument();
    expect(screen.getAllByText('—')).toHaveLength(2);
    const tabs = screen.getByRole('tablist', { name: 'Formato' });
    expect(within(tabs).getByRole('tab', { name: 'Texto' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.queryByTitle('PDF do relatório, versão 1')).not.toBeInTheDocument();

    await user.click(within(tabs).getByRole('tab', { name: 'Ver PDF' }));

    expect(await screen.findByTitle('PDF do relatório, versão 1')).toBeInTheDocument();
    expect(screen.queryByText('Estudo nº 5 de Czerny')).not.toBeInTheDocument();
  });

  it('no computador mostra texto e PDF lado a lado, sem abas', async () => {
    (globalThis as { __testWidth?: number }).__testWidth = 1440;
    mockApi(review);
    renderRoutes(routes, `/lesson-reports/${REPORT}`);

    expect(await screen.findByText('Estudo nº 5 de Czerny')).toBeInTheDocument();
    expect(await screen.findByTitle('PDF do relatório, versão 1')).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
  });

  it('aprovar cita a versão e só navega com a resposta 2xx', async () => {
    const api = mockApi({
      ...review,
      [`POST /api/v1/lesson-reports/${REPORT}/approval`]: {
        body: {
          reportId: REPORT,
          status: 'APPROVED',
          approvedVersion: 1,
          approvedAt: '2026-09-21T13:00:00Z',
        },
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/lesson-reports/${REPORT}`);

    await user.click(await screen.findByRole('button', { name: 'Aprovar relatório · versão 1' }));
    const dialog = screen.getByRole('dialog', { name: 'Aprovar a versão 1?' });
    await user.click(within(dialog).getByRole('button', { name: 'Sim, aprovar a versão 1' }));

    await waitFor(() => expect(router.state.location.pathname).toBe(`/lesson-reports/${REPORT}/approved`));
    const sending = api.calls.find((c) => c.url.endsWith('/approval'))!;
    expect(JSON.parse(sending.init.body as string)).toEqual({ confirmedVersion: 1 });
  });

  it('versão desatualizada mantém a tela e pede para recarregar (08d)', async () => {
    mockApi({
      ...review,
      [`POST /api/v1/lesson-reports/${REPORT}/approval`]: {
        status: 409,
        body: {
          code: 'STALE_VERSION',
          message: 'Esta versão está desatualizada.',
          details: { currentVersion: 2 },
        },
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/lesson-reports/${REPORT}`);

    await user.click(await screen.findByRole('button', { name: 'Aprovar relatório · versão 1' }));
    await user.click(screen.getByRole('button', { name: 'Sim, aprovar a versão 1' }));

    expect(await screen.findByRole('button', { name: 'Recarregar' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/lesson-reports/${REPORT}`);
  });

  it('pedido de alteração por texto mostra a nova versão e o texto entendido', async () => {
    let version = 1;
    mockApi({
      ...review,
      [`GET /api/v1/lesson-reports/${REPORT}`]: () => ({ body: report({ currentVersion: version }) }),
      [`GET /api/v1/lesson-reports/${REPORT}/versions/2/pdf`]: { body: new Blob(['%PDF-']) },
      [`POST /api/v1/lesson-reports/${REPORT}/changes`]: () => {
        version = 2;
        return { status: 202, body: { jobId: 't4' } };
      },
      'GET /api/v1/jobs/t4': {
        body: job({
          jobId: 't4',
          type: 'CHANGE',
          status: 'COMPLETED',
          step: 'READY',
          resultCode: 'NEW_VERSION',
          reportType: 'LESSON',
          reportId: REPORT,
          understoodText: 'Diga que a aula foi ótima.',
        }),
      },
    });
    const user = userEvent.setup();
    renderRoutes(routes, `/lesson-reports/${REPORT}`);

    await user.type(await screen.findByLabelText(/Precisa ajustar algo\?/), 'Diga que a aula foi ótima.');
    await user.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    expect(await screen.findByText('Alteração aplicada. Confira a versão 2.')).toBeInTheDocument();
    expect(screen.getByText('Entendi: “Diga que a aula foi ótima.”')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Aprovar relatório · versão 2' })).toBeInTheDocument();
  });

  it('cancelar pede confirmação e volta ao Painel', async () => {
    mockApi({
      ...review,
      [`POST /api/v1/lesson-reports/${REPORT}/cancellation`]: { status: 204 },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/lesson-reports/${REPORT}`);

    await user.click(await screen.findByRole('button', { name: 'Cancelar relatório' }));
    await user.click(screen.getByRole('button', { name: 'Sim, cancelar' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });
});

describe('TelaAprovado (09)', () => {
  it('mostra a data e a hora da aprovação e os próximos passos', async () => {
    mockApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${REPORT}`]: {
        body: report({ status: 'APPROVED', approvedAt: '2026-09-21T21:42:00Z' }),
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/lesson-reports/${REPORT}/approved`);

    expect(await screen.findByRole('status', { name: '' })).toBeInTheDocument();
    expect(
      screen.getByText('Relatório aprovado em 21/09/2026 às 18:42. Ele já consta no histórico do aluno.'),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ver histórico' }));
    expect(router.state.location.pathname).toBe(`/students/${STUDENT.id}/history`);
  });
});
