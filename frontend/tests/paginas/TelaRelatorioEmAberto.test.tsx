import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rotas } from '../../src/app/rotas';
import { SESSAO_CLARA, renderizarRotas, simularApi } from '../utilitarios';

const ALUNO = '0190a000-0000-7000-8000-00000000000a';
const RELATORIO = '0190a000-0000-7000-8000-0000000000r1';

const INICIO = {
  teacher: SESSAO_CLARA.teacher,
  totalStudents: 1,
  students: [],
  openReports: [],
  audioLimitMb: 50,
};

function aberto(sobrescrever: Record<string, unknown> = {}) {
  return {
    type: 'LESSON',
    reportId: RELATORIO,
    jobId: null,
    studentId: ALUNO,
    studentName: 'Matteo Mafra',
    status: 'AWAITING_REVIEW',
    step: null,
    periodKey: null,
    originChannel: 'TELEGRAM',
    updatedAt: '2026-09-20T12:57:00Z',
    ...sobrescrever,
  };
}

const BASE = {
  'GET /api/v1/session': { corpo: SESSAO_CLARA },
  'GET /api/v1/home': { corpo: INICIO },
  [`GET /api/v1/students/${ALUNO}`]: {
    corpo: {
      id: ALUNO,
      name: 'Matteo Mafra',
      instrument: 'Piano',
      lastLessonAt: null,
      hasOpenReport: true,
    },
  },
};

describe('TelaRelatorioEmAberto (04)', () => {
  it('mostra o canal de origem e a última alteração; continuar vai para a conferência', async () => {
    simularApi({ ...BASE, [`GET /api/v1/students/${ALUNO}/open-report`]: { corpo: aberto() } });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/alunos/${ALUNO}/relatorio-em-aberto`);

    expect(
      await screen.findByRole('heading', { name: 'Você tem um relatório em aberto' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Este aluno já tem um relatório iniciado pelo Telegram e ainda não concluído (última alteração em 20/09/2026 às 09:57).',
      ),
    ).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: 'Continuar de onde parei' }));
    expect(roteador.state.location.pathname).toBe(`/relatorios-aula/${RELATORIO}`);
  });

  it('continuar com perguntas pendentes ou em transcrição vai para a etapa certa', async () => {
    simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO}/open-report`]: {
        corpo: aberto({ reportId: null, jobId: 't9', status: null, step: 'TRANSCRIBING' }),
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/alunos/${ALUNO}/relatorio-em-aberto`);

    await usuario.click(await screen.findByRole('button', { name: 'Continuar de onde parei' }));
    expect(roteador.state.location.pathname).toBe('/trabalhos/t9');
  });

  it('descartar pede confirmação, cancela e só então segue para a gravação', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO}/open-report`]: { corpo: aberto() },
      [`POST /api/v1/lesson-reports/${RELATORIO}/cancellation`]: { status: 204 },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/alunos/${ALUNO}/relatorio-em-aberto`);

    await usuario.click(await screen.findByRole('button', { name: 'Descartar e começar um novo' }));
    const dialogo = screen.getByRole('dialog', { name: 'Descartar o relatório em aberto?' });
    await usuario.click(within(dialogo).getByRole('button', { name: 'Sim, descartar' }));

    await waitFor(() => expect(roteador.state.location.pathname).toBe(`/alunos/${ALUNO}/nova-aula`));
    expect(api.chamadas.some((c) => c.url.endsWith('/cancellation'))).toBe(true);
  });

  it('registro em transcrição é descartado pelo trabalho; erro mantém a tela', async () => {
    simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO}/open-report`]: {
        corpo: aberto({ reportId: null, jobId: 't9', status: null }),
      },
      'POST /api/v1/jobs/t9/discard': {
        status: 409,
        corpo: {
          code: 'ACTION_NOT_ALLOWED_AT_STEP',
          message: 'Esta ação não está disponível nesta etapa do relatório.',
        },
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/alunos/${ALUNO}/relatorio-em-aberto`);

    await usuario.click(await screen.findByRole('button', { name: 'Descartar e começar um novo' }));
    await usuario.click(screen.getByRole('button', { name: 'Sim, descartar' }));

    expect(
      await screen.findByText('Esta ação não está disponível nesta etapa do relatório.'),
    ).toBeInTheDocument();
    expect(roteador.state.location.pathname).toBe(`/alunos/${ALUNO}/relatorio-em-aberto`);
  });

  it('sem relatório em aberto (204) segue direto para a gravação', async () => {
    simularApi({ ...BASE, [`GET /api/v1/students/${ALUNO}/open-report`]: { status: 204 } });
    const { roteador } = renderizarRotas(rotas, `/alunos/${ALUNO}/relatorio-em-aberto`);

    await waitFor(() => expect(roteador.state.location.pathname).toBe(`/alunos/${ALUNO}/nova-aula`));
  });
});
