import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rotas } from '../../src/app/rotas';
import { SESSAO_CLARA, renderizarRotas, simularApi } from '../utilitarios';

const ALUNO = '0190a000-0000-7000-8000-00000000000a';
const AULA = '0190a000-0000-7000-8000-0000000000a1';
const SEMESTRAL = '0190a000-0000-7000-8000-0000000000s1';

const ALUNO_DTO = {
  id: ALUNO,
  name: 'Matteo Mafra',
  instrument: 'Piano',
  lastLessonAt: null,
  hasOpenReport: false,
};

const ITENS = [
  {
    type: 'LESSON',
    reportId: AULA,
    label: 'Aula de 21/09/2026',
    lessonDate: '2026-09-21',
    periodStart: null,
    periodEnd: null,
    approvedAt: '2026-09-21T21:40:00Z',
  },
  {
    type: 'SEMESTER',
    reportId: SEMESTRAL,
    label: 'Semestral (01/01/2026 a 30/06/2026)',
    lessonDate: null,
    periodStart: '2026-01-01',
    periodEnd: '2026-06-30',
    approvedAt: '2026-07-03T13:12:00Z',
  },
];

const BASE = {
  'GET /api/v1/session': { corpo: SESSAO_CLARA },
  [`GET /api/v1/students/${ALUNO}`]: { corpo: ALUNO_DTO },
  [`GET /api/v1/lesson-reports/${AULA}/official-pdf`]: { corpo: new Blob(['%PDF-aula']) },
  [`GET /api/v1/semester-reports/${SEMESTRAL}/official-pdf`]: { corpo: new Blob(['%PDF-sem']) },
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('TelaHistorico (12)', () => {
  it('lista os aprovados do mais recente ao mais antigo com os rótulos do bot', async () => {
    simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO}/history`]: { corpo: { student: ALUNO_DTO, items: ITENS } },
    });
    renderizarRotas(rotas, `/alunos/${ALUNO}/historico`);

    const lista = await screen.findByRole('list', { name: 'Histórico de Matteo Mafra' });
    const itens = within(lista).getAllByRole('listitem');
    expect(itens).toHaveLength(2);
    expect(itens[0]).toHaveTextContent('Aula de 21/09/2026');
    expect(itens[0]).toHaveTextContent('Aprovado em 21/09/2026 às 18:40');
    expect(itens[0]).toHaveTextContent('21SET');
    expect(itens[1]).toHaveTextContent('Semestral (01/01/2026 a 30/06/2026)');
    expect(itens[1]).toHaveTextContent('Aprovado em 03/07/2026 às 10:12');
    expect(within(itens[1]!).getByText('Semestral')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Matteo Mafra' })).toBeInTheDocument();
  });

  it('12b: sem relatórios aprovados oferece registrar aula', async () => {
    simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO}/history`]: { corpo: { student: ALUNO_DTO, items: [] } },
    });
    const { roteador } = renderizarRotas(rotas, `/alunos/${ALUNO}/historico`);

    expect(await screen.findByText('Este aluno ainda não tem relatórios aprovados.')).toBeInTheDocument();
    const vazio = screen
      .getByText('Este aluno ainda não tem relatórios aprovados.')
      .closest('.estado-vazio') as HTMLElement;
    await userEvent.click(within(vazio).getByRole('button', { name: 'Registrar aula' }));
    expect(roteador.state.location.pathname).toBe(`/alunos/${ALUNO}/nova-aula`);
  });

  it('12c: mostra o carregamento enquanto a lista não chega', async () => {
    simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO}/history`]: () => new Promise(() => undefined),
    });
    renderizarRotas(rotas, `/alunos/${ALUNO}/historico`);

    expect(await screen.findByLabelText('Carregando histórico')).toHaveAttribute('aria-busy', 'true');
  });

  it('"Abrir PDF" busca o PDF oficial com a sessão e abre numa nova aba', async () => {
    const api = simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO}/history`]: { corpo: { student: ALUNO_DTO, items: ITENS } },
    });
    const aba = { location: { href: '' } };
    const abrir = vi.spyOn(window, 'open').mockReturnValue(aba as unknown as Window);
    renderizarRotas(rotas, `/alunos/${ALUNO}/historico`);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Abrir PDF — Semestral (01/01/2026 a 30/06/2026)' }),
    );

    await waitFor(() => expect(aba.location.href).toMatch(/^blob:/));
    expect(abrir).toHaveBeenCalledWith('', '_blank');
    const chamada = api.chamadas.find((c) =>
      c.url.endsWith(`/semester-reports/${SEMESTRAL}/official-pdf`),
    );
    expect(chamada?.init.credentials).toBe('same-origin');
  });

  it('aba bloqueada: baixa o arquivo e avisa', async () => {
    simularApi({
      ...BASE,
      [`GET /api/v1/students/${ALUNO}/history`]: { corpo: { student: ALUNO_DTO, items: ITENS } },
    });
    vi.spyOn(window, 'open').mockReturnValue(null);
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      expect(this.download).toBe('relatorio-aula-2026-09-21.pdf');
    });
    renderizarRotas(rotas, `/alunos/${ALUNO}/historico`);

    await userEvent.click(await screen.findByRole('button', { name: 'Abrir PDF — Aula de 21/09/2026' }));

    expect(
      await screen.findByText('O navegador impediu a abertura do PDF; o arquivo foi baixado.'),
    ).toBeInTheDocument();
    expect(clique).toHaveBeenCalledTimes(1);
  });
});
