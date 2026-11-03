import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SchoolHistoryList } from '../../../src/components/admin/SchoolHistoryList';
import { downloadPdf, openPdf } from '../../../src/services/pdf';
import { ApiError } from '../../../src/services/apiClient';
import { renderWithProviders } from '../../utils';
import { HISTORY_TEACHER_INACTIVE, schoolHistoryItem } from '../../support/schoolHistoryFixtures';

vi.mock('../../../src/services/pdf', () => ({
  openPdf: vi.fn(),
  downloadPdf: vi.fn(),
}));

const LESSON = schoolHistoryItem();
const SEMESTER = schoolHistoryItem({
  type: 'SEMESTER',
  reportId: '0190a000-0000-7000-8000-0000000000e2',
  label: 'Semestral (01/07/2026 a 15/12/2026)',
  lessonDate: undefined,
  periodStart: '2026-07-01',
  periodEnd: '2026-12-15',
  teacher: HISTORY_TEACHER_INACTIVE,
  approvedVersion: 1,
});

function renderList(items = [LESSON, SEMESTER], showStudent = true) {
  return renderWithProviders(<SchoolHistoryList items={items} showStudent={showStudent} />);
}

describe('SchoolHistoryList (PDF)', () => {
  beforeEach(() => {
    (globalThis as { __testWidth?: number }).__testWidth = 1440;
    vi.mocked(openPdf).mockReset().mockResolvedValue(true);
    vi.mocked(downloadPdf).mockReset().mockResolvedValue(undefined);
  });

  it('"Abrir PDF" chama openPdf com a rota /pdf do relatório de aula', async () => {
    renderList();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: /Abrir PDF.*Aula de 10\/03\/2026/ }));

    expect(openPdf).toHaveBeenCalledWith(
      `/admin/school-history/lesson-reports/${LESSON.reportId}/pdf`,
      'relatorio-aula-2026-03-10.pdf',
    );
  });

  it('"Baixar PDF" usa a rota /pdf/download e o nome sugerido', async () => {
    renderList();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: /Baixar PDF.*Semestral/ }));

    expect(downloadPdf).toHaveBeenCalledWith(
      `/admin/school-history/semester-reports/${SEMESTER.reportId}/pdf/download`,
      'relatorio-semestral-2026-2.pdf',
    );
    expect(openPdf).not.toHaveBeenCalled();
  });

  it('os botões levam o rótulo do relatório no nome acessível', () => {
    renderList();

    for (const item of [LESSON, SEMESTER]) {
      expect(screen.getByRole('button', { name: new RegExp(`Abrir PDF.*${item.label.replace(/[()/]/g, '.')}`) })).toBeVisible();
      expect(screen.getByRole('button', { name: new RegExp(`Baixar PDF.*${item.label.replace(/[()/]/g, '.')}`) })).toBeVisible();
    }
  });

  it('mostra carregamento só no botão clicado enquanto o PDF vem', async () => {
    let finish: (opened: boolean) => void = () => undefined;
    vi.mocked(openPdf).mockReturnValue(new Promise<boolean>((resolve) => (finish = resolve)));
    renderList();
    const user = userEvent.setup();
    const open = screen.getByRole('button', { name: /Abrir PDF.*Aula de 10\/03\/2026/ });

    await user.click(open);

    expect(open).toBeDisabled();
    expect(open).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByRole('button', { name: /Baixar PDF.*Aula de 10\/03\/2026/ })).toBeEnabled();
    expect(screen.getByRole('button', { name: /Abrir PDF.*Semestral/ })).toBeEnabled();

    finish(true);
    await waitFor(() => expect(open).toBeEnabled());
  });

  it('falha mostra um alerta e mantém a lista na tela', async () => {
    vi.mocked(openPdf).mockRejectedValue(new ApiError(404, { code: 'RESOURCE_NOT_FOUND', message: 'x' }));
    renderList();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: /Abrir PDF.*Aula de 10\/03\/2026/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível obter o PDF. Tente de novo.');
    expect(screen.getByText('Aula de 10/03/2026')).toBeInTheDocument();
    expect(screen.getByText('Semestral (01/07/2026 a 15/12/2026)')).toBeInTheDocument();
  });

  it('navegador que bloqueia a aba avisa que o arquivo foi baixado', async () => {
    vi.mocked(openPdf).mockResolvedValue(false);
    renderList();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: /Abrir PDF.*Aula de 10\/03\/2026/ }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'O navegador impediu a abertura do PDF; o arquivo foi baixado.',
    );
  });

  it('a tabela tem legenda e cabeçalhos; a coluna Aluno some com filtro de aluno', () => {
    const { unmount } = renderList([LESSON, SEMESTER], true);
    const table = screen.getByRole('table');
    expect(within(table).getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
      'Relatório',
      'Aluno',
      'Professor',
      'Aprovado em',
      'Versão',
      'PDF',
    ]);
    expect(within(table).getByText('Inativo')).toBeInTheDocument();
    expect(within(table).getAllByText('10/03/2026 18:30').length).toBeGreaterThan(0);
    expect(within(table).getByText('v3')).toBeInTheDocument();
    unmount();

    renderList([LESSON, SEMESTER], false);
    expect(
      within(screen.getByRole('table'))
        .getAllByRole('columnheader')
        .map((cell) => cell.textContent),
    ).not.toContain('Aluno');
  });
});
