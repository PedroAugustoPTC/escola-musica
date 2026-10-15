import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../src/app/routes';
import { LIGHT_SESSION, renderRoutes, mockApi } from '../utils';

const STUDENT = '0190a000-0000-7000-8000-00000000000a';
const REPORT = '0190a000-0000-7000-8000-0000000000r1';

const HOME = {
  teacher: LIGHT_SESSION.teacher,
  totalStudents: 1,
  students: [],
  openReports: [],
  audioLimitMb: 50,
};

function open(overwrite: Record<string, unknown> = {}) {
  return {
    type: 'LESSON',
    reportId: REPORT,
    jobId: null,
    studentId: STUDENT,
    studentName: 'Matteo Mafra',
    status: 'AWAITING_REVIEW',
    step: null,
    periodKey: null,
    originChannel: 'TELEGRAM',
    updatedAt: '2026-09-20T12:57:00Z',
    ...overwrite,
  };
}

const BASE = {
  'GET /api/v1/session': { body: LIGHT_SESSION },
  'GET /api/v1/home': { body: HOME },
  [`GET /api/v1/students/${STUDENT}`]: {
    body: {
      id: STUDENT,
      name: 'Matteo Mafra',
      instrument: 'Piano',
      lastLessonAt: null,
      hasOpenReport: true,
    },
  },
};

describe('TelaRelatorioEmAberto (04)', () => {
  it('mostra o canal de origem e a última alteração; continuar vai para a conferência', async () => {
    mockApi({ ...BASE, [`GET /api/v1/students/${STUDENT}/open-report`]: { body: open() } });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/students/${STUDENT}/open-report`);

    expect(
      await screen.findByRole('heading', { name: 'Você tem um relatório em aberto' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Este aluno já tem um relatório iniciado pelo Telegram e ainda não concluído (última alteração em 20/09/2026 às 09:57).',
      ),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Continuar de onde parei' }));
    expect(router.state.location.pathname).toBe(`/lesson-reports/${REPORT}`);
  });

  it('continuar com perguntas pendentes ou em transcrição vai para a etapa certa', async () => {
    mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT}/open-report`]: {
        body: open({ reportId: null, jobId: 't9', status: null, step: 'TRANSCRIBING' }),
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/students/${STUDENT}/open-report`);

    await user.click(await screen.findByRole('button', { name: 'Continuar de onde parei' }));
    expect(router.state.location.pathname).toBe('/jobs/t9');
  });

  it('descartar pede confirmação, cancela e só então segue para a gravação', async () => {
    const api = mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT}/open-report`]: { body: open() },
      [`POST /api/v1/lesson-reports/${REPORT}/cancellation`]: { status: 204 },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/students/${STUDENT}/open-report`);

    await user.click(await screen.findByRole('button', { name: 'Descartar e começar um novo' }));
    const dialog = screen.getByRole('dialog', { name: 'Descartar o relatório em aberto?' });
    await user.click(within(dialog).getByRole('button', { name: 'Sim, descartar' }));

    await waitFor(() => expect(router.state.location.pathname).toBe(`/students/${STUDENT}/new-lesson`));
    expect(api.calls.some((c) => c.url.endsWith('/cancellation'))).toBe(true);
  });

  it('registro em transcrição é descartado pelo trabalho; erro mantém a tela', async () => {
    mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT}/open-report`]: {
        body: open({ reportId: null, jobId: 't9', status: null }),
      },
      'POST /api/v1/jobs/t9/discard': {
        status: 409,
        body: {
          code: 'ACTION_NOT_ALLOWED_AT_STEP',
          message: 'Esta ação não está disponível nesta etapa do relatório.',
        },
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, `/students/${STUDENT}/open-report`);

    await user.click(await screen.findByRole('button', { name: 'Descartar e começar um novo' }));
    await user.click(screen.getByRole('button', { name: 'Sim, descartar' }));

    expect(
      await screen.findByText('Esta ação não está disponível nesta etapa do relatório.'),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/students/${STUDENT}/open-report`);
  });

  it('sem relatório em aberto (204) segue direto para a gravação', async () => {
    mockApi({ ...BASE, [`GET /api/v1/students/${STUDENT}/open-report`]: { status: 204 } });
    const { router } = renderRoutes(routes, `/students/${STUDENT}/open-report`);

    await waitFor(() => expect(router.state.location.pathname).toBe(`/students/${STUDENT}/new-lesson`));
  });
});
