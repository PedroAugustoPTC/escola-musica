import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rotas } from '../../src/app/rotas';
import { SESSAO_CLARA, renderizarRotas, simularApi } from '../utilitarios';

const ID = '0190a000-0000-7000-8000-0000000000s1';

function relatorio(sobrescrever: Record<string, unknown> = {}) {
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
    openReportCount: 2,
    lastInteraction: null,
    pdfAvailable: true,
    approvedAt: null,
    updatedAt: '2026-09-21T13:00:00Z',
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
  [`GET /api/v1/semester-reports/${ID}/versions/1/pdf`]: { corpo: new Blob(['%PDF-']) },
};

describe('TelaConferenciaSemestral (11)', () => {
  it('mostra as aulas consideradas, o aviso de em aberto e as 10 seções com Informações Gerais', async () => {
    simularApi({ ...BASE, [`GET /api/v1/semester-reports/${ID}`]: { corpo: relatorio() } });
    renderizarRotas(rotas, `/relatorios-semestrais/${ID}`);

    expect(
      (await screen.findByText('relatório(s) de aula considerado(s) nesta consolidação.')).parentElement,
    ).toHaveTextContent('12 relatório(s) de aula considerado(s) nesta consolidação.');
    expect(
      screen.getByText('Há 2 relatório(s) de aula em aberto neste período; eles não serão considerados.'),
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
      [`GET /api/v1/semester-reports/${ID}`]: { corpo: relatorio() },
      [`POST /api/v1/semester-reports/${ID}/approval`]: {
        corpo: { reportId: ID, status: 'APPROVED', approvedVersion: 1, approvedAt: '2026-09-21T13:00:00Z' },
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/relatorios-semestrais/${ID}`);

    await usuario.click(await screen.findByRole('button', { name: 'Aprovar relatório · versão 1' }));
    await usuario.click(screen.getByRole('button', { name: 'Sim, aprovar a versão 1' }));

    await waitFor(() =>
      expect(roteador.state.location.pathname).toBe(`/relatorios-semestrais/${ID}/aprovado`),
    );
    const envio = api.chamadas.find((c) => c.url.endsWith('/approval'))!;
    expect(JSON.parse(envio.init.body as string)).toEqual({ confirmedVersion: 1 });
  });

  it('alteração por texto envia a versão base e mostra a nova versão', async () => {
    let versao = 1;
    simularApi({
      ...BASE,
      [`GET /api/v1/semester-reports/${ID}`]: () => ({ corpo: relatorio({ currentVersion: versao }) }),
      [`GET /api/v1/semester-reports/${ID}/versions/2/pdf`]: { corpo: new Blob(['%PDF-']) },
      [`POST /api/v1/semester-reports/${ID}/changes`]: (_url, init) => {
        expect(JSON.parse(init.body as string)).toEqual({ text: 'Inclua o recital.', baseVersion: 1 });
        versao = 2;
        return { status: 202, corpo: { jobId: 't9' } };
      },
      'GET /api/v1/jobs/t9': {
        corpo: {
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
      [`GET /api/v1/semester-reports/${ID}`]: {
        corpo: relatorio({ status: 'APPROVED', approvedAt: '2026-09-21T21:42:00Z' }),
      },
    });
    renderizarRotas(rotas, `/relatorios-semestrais/${ID}/aprovado`);

    expect(await screen.findByText(/Relatório semestral · 1º semestre 2026/)).toBeInTheDocument();
    expect(
      screen.getByText('Relatório aprovado em 21/09/2026 às 18:42. Ele já consta no histórico do aluno.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Matteo Mafra')).toBeInTheDocument();
  });
});
