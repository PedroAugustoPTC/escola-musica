import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../src/app/routes';
import { LIGHT_SESSION, renderRoutes, mockApi } from '../utils';

const STUDENT = '0190a000-0000-7000-8000-00000000000a';
const LESSON = '0190a000-0000-7000-8000-0000000000a1';
const SEMESTER = '0190a000-0000-7000-8000-0000000000s1';

const STUDENT_DTO = {
  id: STUDENT,
  name: 'Matteo Mafra',
  instrument: 'Piano',
  lastLessonAt: null,
  hasOpenReport: false,
};

const ITEMS = [
  {
    type: 'LESSON',
    reportId: LESSON,
    label: 'Aula de 21/09/2026',
    lessonDate: '2026-09-21',
    periodStart: null,
    periodEnd: null,
    approvedAt: '2026-09-21T21:40:00Z',
  },
  {
    type: 'SEMESTER',
    reportId: SEMESTER,
    label: 'Semestral (01/01/2026 a 30/06/2026)',
    lessonDate: null,
    periodStart: '2026-01-01',
    periodEnd: '2026-06-30',
    approvedAt: '2026-07-03T13:12:00Z',
  },
];

const BASE = {
  'GET /api/v1/session': { body: LIGHT_SESSION },
  [`GET /api/v1/students/${STUDENT}`]: { body: STUDENT_DTO },
  [`GET /api/v1/lesson-reports/${LESSON}/official-pdf`]: { body: new Blob(['%PDF-lesson']) },
  [`GET /api/v1/semester-reports/${SEMESTER}/official-pdf`]: { body: new Blob(['%PDF-sem']) },
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('TelaHistorico (12)', () => {
  it('lista os aprovados do mais recente ao mais antigo com os rótulos do bot', async () => {
    mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT}/history`]: { body: { student: STUDENT_DTO, items: ITEMS } },
    });
    renderRoutes(routes, `/students/${STUDENT}/history`);

    const list = await screen.findByRole('list', { name: 'Histórico de Matteo Mafra' });
    const items = within(list).getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('Aula de 21/09/2026');
    expect(items[0]).toHaveTextContent('Aprovado em 21/09/2026 às 18:40');
    expect(items[0]).toHaveTextContent('21SET');
    expect(items[1]).toHaveTextContent('Semestral (01/01/2026 a 30/06/2026)');
    expect(items[1]).toHaveTextContent('Aprovado em 03/07/2026 às 10:12');
    expect(within(items[1]!).getByText('Semestral')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Matteo Mafra' })).toBeInTheDocument();
  });

  it('12b: sem relatórios aprovados oferece registrar aula', async () => {
    mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT}/history`]: { body: { student: STUDENT_DTO, items: [] } },
    });
    const { router } = renderRoutes(routes, `/students/${STUDENT}/history`);

    expect(await screen.findByText('Este aluno ainda não tem relatórios aprovados.')).toBeInTheDocument();
    const empty = screen
      .getByText('Este aluno ainda não tem relatórios aprovados.')
      .closest('.empty-state') as HTMLElement;
    await userEvent.click(within(empty).getByRole('button', { name: 'Registrar aula' }));
    expect(router.state.location.pathname).toBe(`/students/${STUDENT}/new-lesson`);
  });

  it('12c: mostra o carregamento enquanto a lista não chega', async () => {
    mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT}/history`]: () => new Promise(() => undefined),
    });
    renderRoutes(routes, `/students/${STUDENT}/history`);

    expect(await screen.findByLabelText('Carregando histórico')).toHaveAttribute('aria-busy', 'true');
  });

  it('"Abrir PDF" busca o PDF oficial com a sessão e abre numa nova aba', async () => {
    const api = mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT}/history`]: { body: { student: STUDENT_DTO, items: ITEMS } },
    });
    const tab = { location: { href: '' } };
    const open = vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
    renderRoutes(routes, `/students/${STUDENT}/history`);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Abrir PDF — Semestral (01/01/2026 a 30/06/2026)' }),
    );

    await waitFor(() => expect(tab.location.href).toMatch(/^blob:/));
    expect(open).toHaveBeenCalledWith('', '_blank');
    const call = api.calls.find((c) => c.url.endsWith(`/semester-reports/${SEMESTER}/official-pdf`));
    expect(call?.init.credentials).toBe('same-origin');
  });

  it('aba bloqueada: baixa o arquivo e avisa', async () => {
    mockApi({
      ...BASE,
      [`GET /api/v1/students/${STUDENT}/history`]: { body: { student: STUDENT_DTO, items: ITEMS } },
    });
    vi.spyOn(window, 'open').mockReturnValue(null);
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      expect(this.download).toBe('relatorio-aula-2026-09-21.pdf');
    });
    renderRoutes(routes, `/students/${STUDENT}/history`);

    await userEvent.click(await screen.findByRole('button', { name: 'Abrir PDF — Aula de 21/09/2026' }));

    expect(
      await screen.findByText('O navegador impediu a abertura do PDF; o arquivo foi baixado.'),
    ).toBeInTheDocument();
    expect(clique).toHaveBeenCalledTimes(1);
  });
});
