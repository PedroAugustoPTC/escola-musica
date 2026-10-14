import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rotas } from '../../src/app/rotas';
import { SESSAO_CLARA, renderizarRotas, simularApi } from '../utilitarios';

const ALUNOS = [
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
    simularApi({
      'GET /api/v1/session': { corpo: SESSAO_CLARA },
      'GET /api/v1/students': { corpo: { result: 'ALL', students: ALUNOS } },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, '/alunos');

    expect(await screen.findByRole('heading', { name: 'Matteo Mafra' })).toBeInTheDocument();
    expect(screen.getByText('2 aluno(s) associado(s) a você.')).toBeInTheDocument();
    expect(screen.getAllByText('Em aberto')).toHaveLength(1);

    await usuario.click(screen.getByRole('button', { name: 'Registrar aula — Matteo Mafra' }));
    expect(roteador.state.location.pathname).toBe(`/alunos/${ALUNOS[0]!.id}/relatorio-em-aberto`);
  });

  it('aluno sem relatório em aberto vai direto para a gravação; semestral e histórico', async () => {
    simularApi({
      'GET /api/v1/session': { corpo: SESSAO_CLARA },
      'GET /api/v1/students': { corpo: { result: 'ALL', students: ALUNOS } },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, '/alunos?registrar=1');
    expect(await screen.findByRole('heading', { name: 'Para qual aluno?' })).toBeInTheDocument();
    await usuario.click(await screen.findByRole('button', { name: 'Registrar aula — Olavo Torrente' }));
    expect(roteador.state.location.pathname).toBe(`/alunos/${ALUNOS[1]!.id}/nova-aula`);
  });

  it('busca sem resultado (03b) informa e mostra todos; limpar busca', async () => {
    const api = simularApi({
      'GET /api/v1/session': { corpo: SESSAO_CLARA },
      'GET /api/v1/students': { corpo: { result: 'ALL', students: ALUNOS } },
      'GET /api/v1/students?search=Beethoven': { corpo: { result: 'NONE', students: ALUNOS } },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizarRotas(rotas, '/alunos');
    await screen.findByRole('heading', { name: 'Matteo Mafra' });
    await usuario.type(screen.getByRole('searchbox', { name: 'Buscar aluno por nome' }), 'Beethoven');

    expect(await screen.findByText('Nenhum aluno encontrado para "Beethoven".')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Olavo Torrente' })).toBeInTheDocument();
    await waitFor(() => expect(roteador.state.location.search).toBe('?busca=Beethoven'));
    expect(api.chamadas.some((c) => c.url.endsWith('search=Beethoven'))).toBe(true);

    await usuario.click(screen.getByRole('button', { name: 'Limpar busca' }));
    await waitFor(() => expect(screen.queryByText(/Nenhum aluno encontrado/)).not.toBeInTheDocument());
  });

  it('carregando (03c) anuncia ocupado', async () => {
    simularApi({
      'GET /api/v1/session': { corpo: SESSAO_CLARA },
      'GET /api/v1/students': () => new Promise(() => undefined),
    });
    renderizarRotas(rotas, '/alunos');
    expect(await screen.findByLabelText('Carregando alunos')).toHaveAttribute('aria-busy', 'true');
  });
});
