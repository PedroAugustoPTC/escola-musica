import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rotas } from '../../src/app/rotas';
import { SESSAO_CLARA, renderizarRotas, simularApi } from '../utilitarios';

const ALUNO = {
  id: '0190a000-0000-7000-8000-00000000000a',
  name: 'Matteo Mafra',
  instrument: 'Piano',
  lastLessonAt: null,
  hasOpenReport: false,
};

const RELATORIO = '0190a000-0000-7000-8000-0000000000r1';

const INICIO = {
  teacher: SESSAO_CLARA.teacher,
  totalStudents: 1,
  students: [ALUNO],
  openReports: [],
  audioLimitMb: 50,
};

function relatorio(sobrescrever: Record<string, unknown> = {}) {
  return {
    reportId: RELATORIO,
    studentId: ALUNO.id,
    studentName: ALUNO.name,
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

function trabalho(sobrescrever: Record<string, unknown> = {}) {
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
    ...sobrescrever,
  };
}

const BASE = {
  'GET /api/v1/session': { corpo: SESSAO_CLARA },
  'GET /api/v1/home': { corpo: INICIO },
};

describe('TelaGravacao (05)', () => {
  it('envio só com áudio; envia o arquivo e segue para o processamento', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO.id}`]: { corpo: ALUNO },
      [`POST /api/v1/students/${ALUNO.id}/lesson-reports`]: { status: 202, corpo: { jobId: 't1' } },
      'GET /api/v1/jobs/t1': { corpo: trabalho() },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/alunos/${ALUNO.id}/nova-aula`);

    expect(await screen.findByRole('heading', { name: 'Matteo Mafra' })).toBeInTheDocument();
    const enviar = screen.getByRole('button', { name: 'Enviar áudio e gerar relatório' });
    expect(enviar).toBeDisabled();
    expect(screen.getAllByText('MP3, M4A, OGG, WAV, WebM ou vídeo MP4 · até 50 MB').length).toBeGreaterThan(
      0,
    );

    await usuario.upload(
      screen.getByLabelText('Arquivo de áudio'),
      new File(['a'], 'aula.m4a', { type: 'audio/mp4' }),
    );
    await usuario.click(enviar);

    await waitFor(() => expect(roteador.state.location.pathname).toBe('/trabalhos/t1'));
    const envio = api.chamadas.find((c) => c.metodo === 'POST')!;
    expect(envio.init.body).toBeInstanceOf(FormData);
    expect((envio.init.body as FormData).get('audio')).toBeInstanceOf(Blob);
  });

  it('relatório em aberto (409) leva à tela 04', async () => {
    simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO.id}`]: { corpo: ALUNO },
      [`POST /api/v1/students/${ALUNO.id}/lesson-reports`]: {
        status: 409,
        corpo: { code: 'REPORT_ALREADY_OPEN', message: 'Você tem um relatório em aberto.' },
      },
      [`GET /api/v1/students/${ALUNO.id}/open-report`]: {
        corpo: {
          type: 'LESSON',
          reportId: RELATORIO,
          jobId: null,
          studentId: ALUNO.id,
          studentName: ALUNO.name,
          status: 'AWAITING_REVIEW',
          step: null,
          periodKey: null,
          originChannel: 'TELEGRAM',
          updatedAt: '2026-09-20T12:57:00Z',
        },
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/alunos/${ALUNO.id}/nova-aula`);

    await usuario.upload(
      await screen.findByLabelText('Arquivo de áudio'),
      new File(['a'], 'aula.m4a', { type: 'audio/mp4' }),
    );
    await usuario.click(screen.getByRole('button', { name: 'Enviar áudio e gerar relatório' }));

    expect(
      await screen.findByRole('heading', { name: 'Você tem um relatório em aberto' }),
    ).toBeInTheDocument();
    expect(roteador.state.location.pathname).toBe(`/alunos/${ALUNO.id}/relatorio-em-aberto`);
  });
});

describe('TelaProcessando (06)', () => {
  it('mostra a etapa atual e, ao concluir com perguntas, vai para a tela de perguntas', async () => {
    let consultas = 0;
    simularApi({
      ...BASE,
      'GET /api/v1/jobs/t1': () => {
        consultas += 1;
        return {
          corpo:
            consultas === 1
              ? trabalho()
              : trabalho({
                  status: 'COMPLETED',
                  step: 'READY',
                  resultCode: 'PENDING_QUESTIONS',
                  reportType: 'LESSON',
                  reportId: RELATORIO,
                }),
        };
      },
      [`GET /api/v1/lesson-reports/${RELATORIO}`]: {
        corpo: relatorio({ status: 'AWAITING_ANSWERS', pendingQuestions: ['Estudou em casa?'] }),
      },
    });
    const { roteador } = renderizarRotas(rotas, '/trabalhos/t1');

    expect(await screen.findByRole('heading', { name: 'Transcrevendo…' })).toBeInTheDocument();
    const etapas = screen.getByRole('list', { name: 'Etapas do processamento' });
    expect(within(etapas).getByText('Transcrevendo').closest('li')).toHaveAttribute('aria-current', 'step');

    await waitFor(
      () => expect(roteador.state.location.pathname).toBe(`/relatorios-aula/${RELATORIO}/perguntas`),
      {
        timeout: 5000,
      },
    );
  });

  it('avisa quando está demorando', async () => {
    simularApi({ ...BASE, 'GET /api/v1/jobs/t1': { corpo: trabalho({ delayed: true }) } });
    renderizarRotas(rotas, '/trabalhos/t1');

    expect(await screen.findByText(/Está demorando mais que o normal/)).toBeInTheDocument();
  });

  it('falha na estruturação oferece tentar de novo sem reenviar o áudio', async () => {
    const api = simularApi({
      ...BASE,
      'GET /api/v1/jobs/t1': {
        corpo: trabalho({
          status: 'FAILED',
          step: 'STRUCTURING',
          resultCode: 'STRUCTURING_FAILED',
          reportType: 'LESSON',
          reportId: RELATORIO,
        }),
      },
      [`POST /api/v1/lesson-reports/${RELATORIO}/retry`]: {
        status: 202,
        corpo: { jobId: 't2' },
      },
      'GET /api/v1/jobs/t2': {
        corpo: trabalho({ jobId: 't2', type: 'RETRY', step: 'STRUCTURING' }),
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, '/trabalhos/t1');

    expect(
      await screen.findByRole('heading', { name: 'Não foi possível processar o áudio.' }),
    ).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Tentar novamente' }));

    await waitFor(() => expect(roteador.state.location.pathname).toBe('/trabalhos/t2'));
    expect(api.chamadas.some((c) => c.url.endsWith('/retry'))).toBe(true);
  });
});

describe('TelaPerguntas (07)', () => {
  it('lista as perguntas numeradas, exige resposta e envia o texto', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${RELATORIO}`]: {
        corpo: relatorio({
          status: 'AWAITING_ANSWERS',
          currentVersion: 0,
          pendingQuestions: ['O aluno estudou em casa?', 'Qual o repertório da próxima aula?'],
        }),
      },
      [`POST /api/v1/lesson-reports/${RELATORIO}/answers`]: { status: 202, corpo: { jobId: 't3' } },
      'GET /api/v1/jobs/t3': {
        corpo: trabalho({ jobId: 't3', type: 'QUESTION_ANSWER', step: 'INCORPORATING_ANSWERS' }),
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/relatorios-aula/${RELATORIO}/perguntas`);

    expect(await screen.findByText('O aluno estudou em casa?')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: 'Enviar resposta' }));
    expect(screen.getByText('Escreva ou grave a sua resposta antes de enviar.')).toBeInTheDocument();

    await usuario.type(screen.getByLabelText('Sua resposta'), 'Estudou sim; vai preparar a valsa.');
    await usuario.click(screen.getByRole('button', { name: 'Enviar resposta' }));

    await waitFor(() => expect(roteador.state.location.pathname).toBe('/trabalhos/t3'));
    const envio = api.chamadas.find((c) => c.metodo === 'POST' && c.url.endsWith('/answers'))!;
    expect(JSON.parse(envio.init.body as string)).toEqual({ text: 'Estudou sim; vai preparar a valsa.' });
  });
});

describe('TelaConferencia (08)', () => {
  const conferencia = {
    ...BASE,
    [`GET /api/v1/lesson-reports/${RELATORIO}`]: { corpo: relatorio() },
    [`GET /api/v1/lesson-reports/${RELATORIO}/versions/1/pdf`]: {
      corpo: new Blob(['%PDF-'], { type: 'application/pdf' }),
    },
  };

  it('no celular mostra as abas Texto e Ver PDF; seções vazias aparecem como —', async () => {
    simularApi(conferencia);
    const usuario = userEvent.setup();
    renderizarRotas(rotas, `/relatorios-aula/${RELATORIO}`);

    expect(
      await screen.findByRole('heading', { name: 'Confira o relatório antes de aprovar' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Versão 1')).toBeInTheDocument();
    expect(screen.getAllByText('—')).toHaveLength(2);
    const abas = screen.getByRole('tablist', { name: 'Formato' });
    expect(within(abas).getByRole('tab', { name: 'Texto' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.queryByTitle('PDF do relatório, versão 1')).not.toBeInTheDocument();

    await usuario.click(within(abas).getByRole('tab', { name: 'Ver PDF' }));

    expect(await screen.findByTitle('PDF do relatório, versão 1')).toBeInTheDocument();
    expect(screen.queryByText('Estudo nº 5 de Czerny')).not.toBeInTheDocument();
  });

  it('no computador mostra texto e PDF lado a lado, sem abas', async () => {
    (globalThis as { __larguraTeste?: number }).__larguraTeste = 1440;
    simularApi(conferencia);
    renderizarRotas(rotas, `/relatorios-aula/${RELATORIO}`);

    expect(await screen.findByText('Estudo nº 5 de Czerny')).toBeInTheDocument();
    expect(await screen.findByTitle('PDF do relatório, versão 1')).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
  });

  it('aprovar cita a versão e só navega com a resposta 2xx', async () => {
    const api = simularApi({
      ...conferencia,
      [`POST /api/v1/lesson-reports/${RELATORIO}/approval`]: {
        corpo: {
          reportId: RELATORIO,
          status: 'APPROVED',
          approvedVersion: 1,
          approvedAt: '2026-09-21T13:00:00Z',
        },
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/relatorios-aula/${RELATORIO}`);

    await usuario.click(await screen.findByRole('button', { name: 'Aprovar relatório · versão 1' }));
    const dialogo = screen.getByRole('dialog', { name: 'Aprovar a versão 1?' });
    await usuario.click(within(dialogo).getByRole('button', { name: 'Sim, aprovar a versão 1' }));

    await waitFor(() =>
      expect(roteador.state.location.pathname).toBe(`/relatorios-aula/${RELATORIO}/aprovado`),
    );
    const envio = api.chamadas.find((c) => c.url.endsWith('/approval'))!;
    expect(JSON.parse(envio.init.body as string)).toEqual({ confirmedVersion: 1 });
  });

  it('versão desatualizada mantém a tela e pede para recarregar (08d)', async () => {
    simularApi({
      ...conferencia,
      [`POST /api/v1/lesson-reports/${RELATORIO}/approval`]: {
        status: 409,
        corpo: {
          code: 'STALE_VERSION',
          message: 'Esta versão está desatualizada.',
          details: { currentVersion: 2 },
        },
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/relatorios-aula/${RELATORIO}`);

    await usuario.click(await screen.findByRole('button', { name: 'Aprovar relatório · versão 1' }));
    await usuario.click(screen.getByRole('button', { name: 'Sim, aprovar a versão 1' }));

    expect(await screen.findByRole('button', { name: 'Recarregar' })).toBeInTheDocument();
    expect(roteador.state.location.pathname).toBe(`/relatorios-aula/${RELATORIO}`);
  });

  it('pedido de alteração por texto mostra a nova versão e o texto entendido', async () => {
    let versao = 1;
    simularApi({
      ...conferencia,
      [`GET /api/v1/lesson-reports/${RELATORIO}`]: () => ({ corpo: relatorio({ currentVersion: versao }) }),
      [`GET /api/v1/lesson-reports/${RELATORIO}/versions/2/pdf`]: { corpo: new Blob(['%PDF-']) },
      [`POST /api/v1/lesson-reports/${RELATORIO}/changes`]: () => {
        versao = 2;
        return { status: 202, corpo: { jobId: 't4' } };
      },
      'GET /api/v1/jobs/t4': {
        corpo: trabalho({
          jobId: 't4',
          type: 'CHANGE',
          status: 'COMPLETED',
          step: 'READY',
          resultCode: 'NEW_VERSION',
          reportType: 'LESSON',
          reportId: RELATORIO,
          understoodText: 'Diga que a aula foi ótima.',
        }),
      },
    });
    const usuario = userEvent.setup();
    renderizarRotas(rotas, `/relatorios-aula/${RELATORIO}`);

    await usuario.type(await screen.findByLabelText(/Precisa ajustar algo\?/), 'Diga que a aula foi ótima.');
    await usuario.click(screen.getByRole('button', { name: 'Solicitar alteração' }));

    expect(await screen.findByText('Alteração aplicada. Confira a versão 2.')).toBeInTheDocument();
    expect(screen.getByText('Entendi: “Diga que a aula foi ótima.”')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Aprovar relatório · versão 2' })).toBeInTheDocument();
  });

  it('cancelar pede confirmação e volta ao Painel', async () => {
    simularApi({
      ...conferencia,
      [`POST /api/v1/lesson-reports/${RELATORIO}/cancellation`]: { status: 204 },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/relatorios-aula/${RELATORIO}`);

    await usuario.click(await screen.findByRole('button', { name: 'Cancelar relatório' }));
    await usuario.click(screen.getByRole('button', { name: 'Sim, cancelar' }));

    await waitFor(() => expect(roteador.state.location.pathname).toBe('/'));
  });
});

describe('TelaAprovado (09)', () => {
  it('mostra a data e a hora da aprovação e os próximos passos', async () => {
    simularApi({
      ...BASE,
      [`GET /api/v1/lesson-reports/${RELATORIO}`]: {
        corpo: relatorio({ status: 'APPROVED', approvedAt: '2026-09-21T21:42:00Z' }),
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, `/relatorios-aula/${RELATORIO}/aprovado`);

    expect(await screen.findByRole('status', { name: '' })).toBeInTheDocument();
    expect(
      screen.getByText('Relatório aprovado em 21/09/2026 às 18:42. Ele já consta no histórico do aluno.'),
    ).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Ver histórico' }));
    expect(roteador.state.location.pathname).toBe(`/alunos/${ALUNO.id}/historico`);
  });
});
