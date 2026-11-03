import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  NO_FILTERS,
  SchoolHistoryFilters,
  type SchoolHistoryFilterValues,
} from '../../../src/components/admin/SchoolHistoryFilters';
import { TEACHER_ACTIVE, TEACHER_INACTIVE } from '../../support/adminFixtures';

function renderFilters(
  props: {
    applied?: SchoolHistoryFilterValues;
    errors?: { field: string; code: string }[];
    onApply?: (values: SchoolHistoryFilterValues) => void;
    onClear?: () => void;
  } = {},
) {
  const onApply = props.onApply ?? vi.fn();
  const onClear = props.onClear ?? vi.fn();
  render(
    <SchoolHistoryFilters
      applied={props.applied ?? NO_FILTERS}
      teachers={[TEACHER_ACTIVE, TEACHER_INACTIVE]}
      errors={props.errors ?? []}
      onApply={onApply}
      onClear={onClear}
    />,
  );
  return { onApply, onClear };
}

function setNarrow(narrow: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: narrow,
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  })) as unknown as typeof window.matchMedia;
}

describe('SchoolHistoryFilters', () => {
  it('lista professores ativos e inativos (inativo marcado) e os tipos Todos, Aula e Semestral', () => {
    setNarrow(false);
    renderFilters();

    const teacher = screen.getByRole('combobox', { name: 'Professor' });
    expect(within(teacher).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Todos',
      'Clara Souza',
      'Bruno Lima (inativo)',
    ]);
    const type = screen.getByRole('combobox', { name: 'Tipo' });
    expect(within(type).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Todos',
      'Aula',
      'Semestral',
    ]);
    expect(screen.getByLabelText('De')).toBeInTheDocument();
    expect(screen.getByLabelText('Até')).toBeInTheDocument();
  });

  it('"Aplicar" entrega o rascunho escolhido; nada muda antes disso', async () => {
    setNarrow(false);
    const { onApply } = renderFilters();
    const user = userEvent.setup();

    await user.selectOptions(screen.getByRole('combobox', { name: 'Professor' }), TEACHER_INACTIVE.id);
    await user.selectOptions(screen.getByRole('combobox', { name: 'Tipo' }), 'SEMESTER');
    await user.type(screen.getByLabelText('De'), '2026-03-01');
    await user.type(screen.getByLabelText('Até'), '2026-03-31');
    expect(onApply).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Aplicar' }));

    expect(onApply).toHaveBeenCalledWith({
      teacherId: TEACHER_INACTIVE.id,
      type: 'SEMESTER',
      from: '2026-03-01',
      to: '2026-03-31',
    });
  });

  it('"Limpar filtros" chama onClear', async () => {
    setNarrow(false);
    const { onClear } = renderFilters({ applied: { ...NO_FILTERS, type: 'LESSON' } });

    await userEvent.setup().click(screen.getByRole('button', { name: 'Limpar filtros' }));

    expect(onClear).toHaveBeenCalledTimes(1);
  });

  it('mostra o contador de filtros ativos', () => {
    setNarrow(false);
    renderFilters({ applied: { teacherId: TEACHER_ACTIVE.id, type: 'LESSON', from: '', to: '' } });

    expect(screen.getByText('2 filtros ativos')).toBeInTheDocument();
  });

  it('erros do servidor aparecem junto do campo, ligados por aria-describedby', () => {
    setNarrow(false);
    renderFilters({
      errors: [
        { field: 'from', code: 'INVALID_DATE' },
        { field: 'to', code: 'PERIOD_START_AFTER_END' },
      ],
    });

    const from = screen.getByLabelText('De');
    expect(from).toHaveAttribute('aria-invalid', 'true');
    expect(from).toHaveAccessibleDescription('Data inválida. Use o formato dd/mm/aaaa.');
    expect(screen.getByLabelText('Até')).toHaveAccessibleDescription(
      'A data inicial deve ser igual ou anterior à data final.',
    );
  });

  it('abaixo de 640 px os filtros começam recolhidos e o botão os abre com aria-expanded', async () => {
    setNarrow(true);
    renderFilters({ applied: { ...NO_FILTERS, teacherId: TEACHER_ACTIVE.id } });
    const user = userEvent.setup();

    const toggle = screen.getByRole('button', { name: /^Filtros \(1 ativo\)/ });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('combobox', { name: 'Professor' })).not.toBeInTheDocument();

    await user.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('combobox', { name: 'Professor' })).toBeInTheDocument();
  });
});
