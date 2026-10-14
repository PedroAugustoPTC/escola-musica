import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rotas } from '../../src/app/rotas';
import { SESSAO_CLARA, renderizarRotas, simularApi } from '../utilitarios';

const ALUNO = '0190a000-0000-7000-8000-00000000000a';
const SEMESTRAL = '0190a000-0000-7000-8000-0000000000s1';

const SEMESTRES = [
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

function previa(chave: string, sobrescrever: Record<string, unknown> = {}) {
  return {
    periodKey: chave,
    approvedLessons: 3,
    openLessonReports: 0,
    approvedSemesterReportId: null,
    openSemesterReport: null,
    ...sobrescrever,
  };
}

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
  [`GET /api/v1/students/${ALUNO}`]: {
    corpo: {
      id: ALUNO,
      name: 'Matteo Mafra',
      instrument: 'Piano',
      lastLessonAt: null,
      hasOpenReport: false,
    },
  },
  [`GET /api/v1/students/${ALUNO}/semesters`]: { corpo: SEMESTRES },
};

describe('TelaSemestral (10)', () => {
  it('pré-seleciona o mais recente, mostra o intervalo e gera pelo semestre escolhido', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO}/semesters/2026-2/preview`]: {
        corpo: previa('2026-2', { approvedLessons: 0 }),
      },
      [`GET /api/v1/students/${ALUNO}/semesters/2026-1/preview`]: { corpo: previa('2026-1') },
      [`POST /api/v1/students/${ALUNO}/semester-reports`]: { status: 202, corpo: { jobId: 't7' } },
      'GET /api/v1/jobs/t7': {
        corpo: {
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
      periodKey: '2026-1',
      confirmDespiteOpenReports: false,
    });
  });

  it('relatórios de aula em aberto pedem "Gerar mesmo assim"', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO}/semesters/2026-2/preview`]: {
        corpo: previa('2026-2', { openLessonReports: 2 }),
      },
      [`POST /api/v1/students/${ALUNO}/semester-reports`]: { status: 202, corpo: { jobId: 't8' } },
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
      periodKey: '2026-2',
      confirmDespiteOpenReports: true,
    });
  });

  it('semestral já aprovado e semestral em aberto com continuar ou descartar', async () => {
    let cancelado = false;
    simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO}/semesters/2026-2/preview`]: () => ({
        corpo: previa('2026-2', {
          openSemesterReport: cancelado
            ? null
            : {
                type: 'SEMESTER',
                reportId: SEMESTRAL,
                jobId: null,
                studentId: ALUNO,
                studentName: 'Matteo Mafra',
                status: 'AWAITING_REVIEW',
                step: null,
                periodKey: '2026-2',
                originChannel: 'TELEGRAM',
                updatedAt: '2026-09-20T12:57:00Z',
              },
        }),
      }),
      [`GET /api/v1/students/${ALUNO}/semesters/2026-1/preview`]: {
        corpo: previa('2026-1', { approvedSemesterReportId: SEMESTRAL }),
      },
      [`POST /api/v1/semester-reports/${SEMESTRAL}/cancellation`]: () => {
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
    simularApi({ ...BASE, [`GET /api/v1/students/${ALUNO}/semesters`]: { corpo: [] } });
    renderizarRotas(rotas, `/alunos/${ALUNO}/semestral`);

    expect(await screen.findByText('Nenhum semestre disponível para este aluno ainda.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Gerar relatório semestral' })).not.toBeInTheDocument();
  });
});
