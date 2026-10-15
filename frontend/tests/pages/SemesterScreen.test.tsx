import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../src/app/routes';
import { LIGHT_SESSION, renderRoutes, mockApi } from '../utils';

const STUDENT = '0190a000-0000-7000-8000-00000000000a';
const SEMESTER = '0190a000-0000-7000-8000-0000000000s1';

const SEMESTERS = [
  {
    key: '2026-2',
    label: '2º semestre 2026',
    start: '2026-07-01',
    end: '2026-12-31',
    inProgress: true,
    preselected: true,
  },
  {
    key: '2026-1',
    label: '1º semestre 2026',
    start: '2026-01-01',
    end: '2026-06-30',
    inProgress: false,
    preselected: false,
  },
];

function preview(key: string, overwrite: Record<string, unknown> = {}) {
  return {
    periodKey: key,
    approvedLessons: 3,
    openLessonReports: 0,
    approvedSemesterReportId: null,
    openSemesterReport: null,
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
  [`GET /api/v1/students/${STUDENT}`]: {
    body: {
      id: STUDENT,
      name: 'Matteo Mafra',
      instrument: 'Piano',
      lastLessonAt: null,
      hasOpenReport: false,
    },
  },
  [`GET /api/v1/students/${STUDENT}/semesters`]: { body: SEMESTERS },
};

describe('TelaSemestral (10)', () => {
  it('pré-seleciona o mais recente, mostra o intervalo e gera pelo semestre escolhido', async () => {
    const api = mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT}/semesters/2026-2/preview`]: {
        body: preview('2026-2', { approvedLessons: 0 }),
      },
      [`GET /api/v1/students/${STUDENT}/semesters/2026-1/preview`]: { body: preview('2026-1') },
      [`POST /api/v1/students/${STUDENT}/semester-reports`]: { status: 202, body: { jobId: 't7' } },
      'GET /api/v1/jobs/t7': {
        body: {
          jobId: 't7',
          type: 'SEMESTER_CONSOLIDATION',
          status: 'RUNNING',
          step: 'CONSOLIDATING',
          delayed: false,
          resultCode: null,
          reportType: 'SEMESTER',
          reportId: null,
          understoodText: null,
        },
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/students/${STUDENT}/semester`);

    const group = await screen.findByRole('radiogroup', { name: 'Período' });
    expect(within(group).getByRole('radio', { name: /2º semestre 2026/ })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(screen.getByText('2º semestre 2026: 01/07/2026 a 31/12/2026')).toBeInTheDocument();
    // 10b: sem aulas aprovadas no período
    expect(await screen.findByText(/Nenhum relatório de aula aprovado neste período/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gerar relatório semestral' })).toBeDisabled();

    await user.click(within(group).getByRole('radio', { name: /1º semestre 2026/ }));
    expect(
      await screen.findByText('O 1º semestre 2026 tem 3 relatório(s) de aula aprovado(s).'),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Gerar relatório semestral' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/jobs/t7'));
    const sending = api.calls.find((c) => c.method === 'POST')!;
    expect(JSON.parse(sending.init.body as string)).toEqual({
      periodKey: '2026-1',
      confirmDespiteOpenReports: false,
    });
  });

  it('relatórios de aula em aberto pedem "Gerar mesmo assim"', async () => {
    const api = mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT}/semesters/2026-2/preview`]: {
        body: preview('2026-2', { openLessonReports: 2 }),
      },
      [`POST /api/v1/students/${STUDENT}/semester-reports`]: { status: 202, body: { jobId: 't8' } },
    });
    const user = userEvent.setup();
    renderRoutes(routes, `/students/${STUDENT}/semester`);

    expect(
      await screen.findByText(
        'Há 2 relatório(s) de aula em aberto neste período; eles não serão considerados.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gerar relatório semestral' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Gerar mesmo assim' }));

    await waitFor(() => expect(api.calls.some((c) => c.method === 'POST')).toBe(true));
    const sending = api.calls.find((c) => c.method === 'POST')!;
    expect(JSON.parse(sending.init.body as string)).toEqual({
      periodKey: '2026-2',
      confirmDespiteOpenReports: true,
    });
  });

  it('semestral já aprovado e semestral em aberto com continuar ou descartar', async () => {
    let cancelled = false;
    mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT}/semesters/2026-2/preview`]: () => ({
        body: preview('2026-2', {
          openSemesterReport: cancelled
            ? null
            : {
                type: 'SEMESTER',
                reportId: SEMESTER,
                jobId: null,
                studentId: STUDENT,
                studentName: 'Matteo Mafra',
                status: 'AWAITING_REVIEW',
                step: null,
                periodKey: '2026-2',
                originChannel: 'TELEGRAM',
                updatedAt: '2026-09-20T12:57:00Z',
              },
        }),
      }),
      [`GET /api/v1/students/${STUDENT}/semesters/2026-1/preview`]: {
        body: preview('2026-1', { approvedSemesterReportId: SEMESTER }),
      },
      [`POST /api/v1/semester-reports/${SEMESTER}/cancellation`]: () => {
        cancelled = true;
        return { status: 204 };
      },
    });
    const user = userEvent.setup();
    renderRoutes(routes, `/students/${STUDENT}/semester`);

    expect(await screen.findByText(/iniciado pelo Telegram e ainda não concluído/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continuar de onde parei' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Descartar e começar um novo' }));
    await user.click(screen.getByRole('button', { name: 'Sim, descartar' }));
    expect(
      await screen.findByText('O 2º semestre 2026 tem 3 relatório(s) de aula aprovado(s).'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: /1º semestre 2026/ }));
    expect(
      await screen.findByText('O relatório semestral deste período já foi aprovado.'),
    ).toBeInTheDocument();
  });

  it('10c: nenhum semestre disponível', async () => {
    mockApi({ ...BASE, [`GET /api/v1/students/${STUDENT}/semesters`]: { body: [] } });
    renderRoutes(routes, `/students/${STUDENT}/semester`);

    expect(await screen.findByText('Nenhum semestre disponível para este aluno ainda.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Gerar relatório semestral' })).not.toBeInTheDocument();
  });
});
