import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../src/app/routes';
import { LIGHT_SESSION, renderRoutes, mockApi } from '../utils';

const ID = '0190a000-0000-7000-8000-0000000000s1';

function report(overwrite: Record<string, unknown> = {}) {
  return {
    reportId: ID,
    studentId: '0190a000-0000-7000-8000-00000000000a',
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
    openReportCount: 2,
    lastInteraction: null,
    pdfAvailable: true,
    approvedAt: null,
    updatedAt: '2026-09-21T13:00:00Z',
    ...overwrite,
  };
}

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
  [`GET /api/v1/semester-reports/${ID}/versions/1/pdf`]: { body: new Blob(['%PDF-']) },
};

describe('TelaConferenciaSemestral (11)', () => {
  it('mostra as aulas consideradas, o aviso de em aberto e as 10 seções com Informações Gerais', async () => {
    mockApi({ ...BASE, [`GET /api/v1/semester-reports/${ID}`]: { body: report() } });
    renderRoutes(routes, `/semester-reports/${ID}`);

    expect(
      (await screen.findByText('relatório(s) de aula considerado(s) nesta consolidação.')).parentElement,
    ).toHaveTextContent('12 relatório(s) de aula considerado(s) nesta consolidação.');
    expect(
      screen.getByText('Há 2 relatório(s) de aula em aberto neste período; eles não serão considerados.'),
    ).toBeInTheDocument();
    const text = screen.getByRole('article', { name: 'Relatório em texto' });
    expect(within(text).getAllByRole('heading', { level: 2 })).toHaveLength(10);
    expect(within(text).getByText('14 anos')).toBeInTheDocument();
    expect(within(text).getByText('Bom semestre.')).toBeInTheDocument();
    expect(within(text).getAllByText('—').length).toBeGreaterThan(5);
  });

  it('aprova citando a versão e vai para a tela de aprovado', async () => {
    const api = mockApi({
      ...BASE,
      [`GET /api/v1/semester-reports/${ID}`]: { body: report() },
      [`POST /api/v1/semester-reports/${ID}/approval`]: {
        body: { reportId: ID, status: 'APPROVED', approvedVersion: 1, approvedAt: '2026-09-21T13:00:00Z' },
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/semester-reports/${ID}`);

    await user.click(await screen.findByRole('button', { name: 'Aprovar relatório · versão 1' }));
    await user.click(screen.getByRole('button', { name: 'Sim, aprovar a versão 1' }));

    await waitFor(() => expect(router.state.location.pathname).toBe(`/semester-reports/${ID}/approved`));
    const sending = api.calls.find((c) => c.url.endsWith('/approval'))!;
    expect(JSON.parse(sending.init.body as string)).toEqual({ confirmedVersion: 1 });
  });

  it('alteração por texto envia a versão base e mostra a nova versão', async () => {
    let version = 1;
    mockApi({
      ...BASE,
      [`GET /api/v1/semester-reports/${ID}`]: () => ({ body: report({ currentVersion: version }) }),
      [`GET /api/v1/semester-reports/${ID}/versions/2/pdf`]: { body: new Blob(['%PDF-']) },
      [`POST /api/v1/semester-reports/${ID}/changes`]: (_url, init) => {
        expect(JSON.parse(init.body as string)).toEqual({ text: 'Inclua o recital.', baseVersion: 1 });
        version = 2;
        return { status: 202, body: { jobId: 't9' } };
      },
      'GET /api/v1/jobs/t9': {
        body: {
          jobId: 't9',
          type: 'CHANGE',
          status: 'COMPLETED',
          step: 'READY',
          delayed: false,
          resultCode: 'NEW_VERSION',
          reportType: 'SEMESTER',
          reportId: ID,
          understoodText: 'Inclua o recital.',
        },
      },
    });
    const user = userEvent.setup();
    renderRoutes(routes, `/semester-reports/${ID}`);

    await user.type(await screen.findByLabelText(/Precisa ajustar algo\?/), 'Inclua o recital.');
    await user.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    expect(await screen.findByText('Alteração aplicada. Confira a versão 2.')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Aprovar relatório · versão 2' })).toBeInTheDocument();
  });

  it('semestral aprovado mostra a variação do 09', async () => {
    mockApi({
      ...BASE,
      [`GET /api/v1/semester-reports/${ID}`]: {
        body: report({ status: 'APPROVED', approvedAt: '2026-09-21T21:42:00Z' }),
      },
    });
    renderRoutes(routes, `/semester-reports/${ID}/approved`);

    expect(await screen.findByText(/Relatório semestral · 1º semestre 2026/)).toBeInTheDocument();
    expect(
      screen.getByText('Relatório aprovado em 21/09/2026 às 18:42. Ele já consta no histórico do aluno.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Matteo Mafra')).toBeInTheDocument();
  });
});
