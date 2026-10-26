import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../src/app/routes';
import { LIGHT_SESSION, renderRoutes, mockApi } from '../utils';

const MATTEO = {
  id: '0190a000-0000-7000-8000-00000000000a',
  name: 'Matteo Mafra',
  instrument: 'Piano',
  lastLessonAt: '2026-09-21',
  hasOpenReport: true,
};

const HOME = {
  teacher: LIGHT_SESSION.teacher,
  totalStudents: 7,
  students: [MATTEO],
  openReports: [
    {
      type: 'LESSON',
      reportId: '0190a000-0000-7000-8000-0000000000aa',
      jobId: null,
      studentId: MATTEO.id,
      studentName: 'Matteo Mafra',
      status: 'AWAITING_REVIEW',
      step: null,
      periodKey: null,
      originChannel: 'TELEGRAM',
      updatedAt: '2026-09-20T12:57:00Z',
    },
  ],
  audioLimitMb: 50,
};

describe('TelaPainel (02)', () => {
  it('mostra saudação, relatórios em aberto, total e primeiros alunos', async () => {
    mockApi({ 'GET /api/v1/session': { body: LIGHT_SESSION }, 'GET /api/v1/home': { body: HOME } });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, '/');

    expect(await screen.findByRole('heading', { name: 'Olá, Clara' })).toBeInTheDocument();
    const notice = screen.getByRole('status');
    expect(notice).toHaveTextContent('Matteo Mafra · Relatório de aula · Aguardando conferência');
    expect(notice).toHaveTextContent('última alteração em 20/09/2026 09:57');
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('Piano · última aula 21/09/2026')).toBeInTheDocument();
    expect(screen.getByText('Em aberto')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Continuar: Matteo Mafra/ }));
    expect(router.state.location.pathname).toBe(`/students/${MATTEO.id}/open-report`);
  });

  it('tem o atalho Meus alunos', async () => {
    mockApi({ 'GET /api/v1/session': { body: LIGHT_SESSION }, 'GET /api/v1/home': { body: HOME } });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, '/');
    await user.click(await screen.findByRole('button', { name: 'Meus alunos' }));
    expect(router.state.location.pathname).toBe('/my-students');
  });

  it('registrar aula leva à escolha do aluno', async () => {
    mockApi({ 'GET /api/v1/session': { body: LIGHT_SESSION }, 'GET /api/v1/home': { body: HOME } });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, '/');
    await user.click(await screen.findByRole('button', { name: /Grave um áudio/ }));
    expect(router.state.location.search).toBe('?register=1');
  });

  it('vazio (02b) quando não há alunos', async () => {
    mockApi({
      'GET /api/v1/session': { body: LIGHT_SESSION },
      'GET /api/v1/home': { body: { ...HOME, totalStudents: 0, students: [], openReports: [] } },
    });
    renderRoutes(routes, '/');
    expect(await screen.findByText('Nenhum aluno por aqui')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Meus alunos' })).toBeInTheDocument();
    expect(
      screen.getByText('Você ainda não tem alunos. Cadastre o primeiro em “Meus alunos”.'),
    ).toBeInTheDocument();
  });

  it('sem alunos, orienta cadastrar o primeiro e leva ao formulário', async () => {
    mockApi({
      'GET /api/v1/session': { body: LIGHT_SESSION },
      'GET /api/v1/home': { body: { ...HOME, totalStudents: 0, students: [], openReports: [] } },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, '/');
    await user.click(await screen.findByRole('button', { name: 'Cadastrar o primeiro aluno' }));
    expect(router.state.location.pathname).toBe('/my-students/new');
  });

  it('com alunos não mostra a orientação de cadastrar o primeiro', async () => {
    mockApi({ 'GET /api/v1/session': { body: LIGHT_SESSION }, 'GET /api/v1/home': { body: HOME } });
    renderRoutes(routes, '/');
    await screen.findByRole('heading', { name: 'Olá, Clara' });
    expect(screen.queryByRole('button', { name: 'Cadastrar o primeiro aluno' })).not.toBeInTheDocument();
  });

  it('menu (02c) com tema e encerrar sessão; 401 leva a Entrar', async () => {
    mockApi({
      'GET /api/v1/session': { body: LIGHT_SESSION },
      'GET /api/v1/home': {
        status: 401,
        body: {
          code: 'SESSION_EXPIRED',
          message: 'Sua sessão expirou. Entre novamente com o seu código.',
        },
      },
    });
    const { router } = renderRoutes(routes, '/');
    await waitFor(() => expect(router.state.location.pathname).toBe('/sign-in'));
    expect(router.state.location.search).toContain('reason=SESSION_EXPIRED');
  });
});
