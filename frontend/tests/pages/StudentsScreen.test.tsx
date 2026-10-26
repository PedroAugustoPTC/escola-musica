import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../src/app/routes';
import { LIGHT_SESSION, renderRoutes, mockApi } from '../utils';

const STUDENTS = [
  {
    id: '0190a000-0000-7000-8000-00000000000a',
    name: 'Matteo Mafra',
    instrument: 'Piano',
    lastLessonAt: '2026-09-21',
    hasOpenReport: true,
  },
  {
    id: '0190a000-0000-7000-8000-00000000000b',
    name: 'Olavo Torrente',
    instrument: null,
    lastLessonAt: null,
    hasOpenReport: false,
  },
];

describe('TelaAlunos (03)', () => {
  it('lista os alunos com a marca Em aberto e as ações', async () => {
    mockApi({
      'GET /api/v1/session': { body: LIGHT_SESSION },
      'GET /api/v1/students': { body: { result: 'ALL', students: STUDENTS } },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, '/students');

    expect(await screen.findByRole('heading', { name: 'Matteo Mafra' })).toBeInTheDocument();
    expect(screen.getByText('2 aluno(s) associado(s) a você.')).toBeInTheDocument();
    expect(screen.getAllByText('Em aberto')).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: 'Registrar aula — Matteo Mafra' }));
    expect(router.state.location.pathname).toBe(`/students/${STUDENTS[0]!.id}/open-report`);
  });

  it('aluno sem relatório em aberto vai direto para a gravação; semestral e histórico', async () => {
    mockApi({
      'GET /api/v1/session': { body: LIGHT_SESSION },
      'GET /api/v1/students': { body: { result: 'ALL', students: STUDENTS } },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, '/students?register=1');
    expect(await screen.findByRole('heading', { name: 'Para qual aluno?' })).toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Registrar aula — Olavo Torrente' }));
    expect(router.state.location.pathname).toBe(`/students/${STUDENTS[1]!.id}/new-lesson`);
  });

  it('busca sem resultado (03b) informa e mostra todos; limpar busca', async () => {
    const api = mockApi({
      'GET /api/v1/session': { body: LIGHT_SESSION },
      'GET /api/v1/students': { body: { result: 'ALL', students: STUDENTS } },
      'GET /api/v1/students?search=Beethoven': { body: { result: 'NONE', students: STUDENTS } },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, '/students');
    await screen.findByRole('heading', { name: 'Matteo Mafra' });
    await user.type(screen.getByRole('searchbox', { name: 'Buscar aluno por nome' }), 'Beethoven');

    expect(await screen.findByText('Nenhum aluno encontrado para "Beethoven".')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Olavo Torrente' })).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.search).toBe('?search=Beethoven'));
    expect(api.calls.some((c) => c.url.endsWith('search=Beethoven'))).toBe(true);

    await user.click(screen.getByRole('button', { name: 'Limpar busca' }));
    await waitFor(() => expect(screen.queryByText(/Nenhum aluno encontrado/)).not.toBeInTheDocument());
  });

  it('a escolha de aluno para aula pede e mostra só alunos ativos', async () => {
    const api = mockApi({
      'GET /api/v1/session': { body: LIGHT_SESSION },
      'GET /api/v1/students': { body: { result: 'ALL', students: STUDENTS } },
    });
    renderRoutes(routes, '/students?register=1');
    expect(await screen.findByRole('heading', { name: 'Para qual aluno?' })).toBeInTheDocument();
    await screen.findByRole('heading', { name: 'Matteo Mafra' });
    // nenhum filtro de situação: o servidor só devolve ativos (a lista de inativos fica em Meus alunos)
    const calls = api.calls.filter((c) => c.url.startsWith('/api/v1/students'));
    expect(calls.length).toBeGreaterThan(0);
    expect(calls.every((c) => !/status|inactive|active/i.test(c.url))).toBe(true);
    expect(screen.queryByText('Inativo')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Reativar/ })).not.toBeInTheDocument();
  });

  it('carregando (03c) anuncia ocupado', async () => {
    mockApi({
      'GET /api/v1/session': { body: LIGHT_SESSION },
      'GET /api/v1/students': () => new Promise(() => undefined),
    });
    renderRoutes(routes, '/students');
    expect(await screen.findByLabelText('Carregando alunos')).toHaveAttribute('aria-busy', 'true');
  });
});
