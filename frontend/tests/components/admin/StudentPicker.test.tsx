import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StudentPicker } from '../../../src/components/admin/StudentPicker';
import type { AdminStudent } from '../../../src/services/admin/students';
import { TEACHER_ACTIVE, TEACHER_INACTIVE, TEST_CPF, administratorStudent, pageOf } from '../../support/adminFixtures';
import { mockApi, renderWithProviders } from '../../utils';

const LIST = 'GET /api/v1/admin/students';

const MATTEO = administratorStudent();
const HELENA = administratorStudent({
  id: '0190a000-0000-7000-8000-0000000000d2',
  name: 'Helena Prado',
  active: false,
  teachers: [
    { id: TEACHER_ACTIVE.id, name: 'Clara Souza', active: true },
    { id: TEACHER_INACTIVE.id, name: 'Bruno Lima', active: false },
  ],
});

/** Guarda o termo confirmado como a tela guarda na rota. */
function Harness({ onSelect }: { onSelect: (student: AdminStudent) => void }) {
  const [term, setTerm] = useState('');
  return (
    <StudentPicker
      search={term}
      refusedFromUrl={false}
      onDismissRefusal={() => undefined}
      onSearchChange={setTerm}
      onSelect={onSelect}
    />
  );
}

describe('StudentPicker', () => {
  it('busca com status=ALL e mostra nome, "Inativo" e os professores de cada opção', async () => {
    const api = mockApi({ [LIST]: { body: pageOf([MATTEO, HELENA]) } });
    renderWithProviders(<Harness onSelect={vi.fn()} />);
    const user = userEvent.setup();

    await user.type(screen.getByRole('combobox', { name: 'Procurar aluno' }), 'a');

    const listbox = await screen.findByRole('listbox');
    const options = within(listbox).getAllByRole('option');
    expect(options).toHaveLength(2);
    expect(options[0]).toHaveTextContent('Matteo Mafra');
    expect(options[0]).toHaveTextContent('Clara Souza');
    expect(options[0]).not.toHaveTextContent('Inativo');
    expect(options[1]).toHaveTextContent('Helena Prado');
    expect(options[1]).toHaveTextContent('Inativo');
    expect(options[1]).toHaveTextContent(/Bruno Lima \(inativo\)/);
    const search = api.calls.find((call) => call.url.includes('/admin/students'))!;
    expect(search.url).toContain('status=ALL');
    expect(search.url).toContain('search=a');
  });

  it('sem termo não consulta nada', async () => {
    const api = mockApi({ [LIST]: { body: pageOf([MATTEO]) } });
    renderWithProviders(<Harness onSelect={vi.fn()} />);

    expect(screen.getByRole('combobox', { name: 'Procurar aluno' })).toHaveAttribute('aria-expanded', 'false');
    expect(api.calls).toEqual([]);
  });

  it('escolher uma opção devolve o aluno', async () => {
    mockApi({ [LIST]: { body: pageOf([MATTEO, HELENA]) } });
    const onSelect = vi.fn();
    renderWithProviders(<Harness onSelect={onSelect} />);
    const user = userEvent.setup();

    await user.type(screen.getByRole('combobox', { name: 'Procurar aluno' }), 'He');
    await user.click(await screen.findByRole('option', { name: /Helena Prado/ }));

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect.mock.calls[0]![0]).toMatchObject({ id: HELENA.id });
  });

  it('funciona pelo teclado: seta para baixo e Enter escolhem; Esc fecha a lista', async () => {
    mockApi({ [LIST]: { body: pageOf([MATTEO, HELENA]) } });
    const onSelect = vi.fn();
    renderWithProviders(<Harness onSelect={onSelect} />);
    const user = userEvent.setup();
    const combobox = screen.getByRole('combobox', { name: 'Procurar aluno' });

    await user.type(combobox, 'a');
    await screen.findByRole('listbox');
    await user.keyboard('{ArrowDown}{ArrowDown}');
    expect(combobox.getAttribute('aria-activedescendant')).toBe(
      screen.getByRole('option', { name: /Helena Prado/ }).id,
    );
    await user.keyboard('{Enter}');
    expect(onSelect.mock.calls[0]![0]).toMatchObject({ id: HELENA.id });

    await user.type(combobox, 'b');
    await screen.findByRole('listbox');
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('listbox')).not.toBeInTheDocument());
  });

  it.each([TEST_CPF, '111.444.777-35', '111 444 777 35'])(
    'termo com forma de CPF (%s) é recusado e nunca chega à API',
    async (term) => {
      const api = mockApi({ [LIST]: { body: pageOf([MATTEO]) } });
      renderWithProviders(<Harness onSelect={vi.fn()} />);
      const user = userEvent.setup();
      const combobox = screen.getByRole('combobox', { name: 'Procurar aluno' });

      await user.type(combobox, term);

      expect(
        await screen.findByText('Não há busca por CPF. Procure pelo nome ou pelo instrumento.'),
      ).toBeInTheDocument();
      expect(combobox).toHaveAttribute('aria-invalid', 'true');
      // espera além do tempo de espera da digitação: nada pode ter saído
      await new Promise((resolve) => setTimeout(resolve, 500));
      expect(api.calls).toEqual([]);
    },
  );

  it('busca sem resultado diz que nenhum aluno foi encontrado', async () => {
    mockApi({ [LIST]: { body: pageOf([]) } });
    renderWithProviders(<Harness onSelect={vi.fn()} />);
    const user = userEvent.setup();

    await user.type(screen.getByRole('combobox', { name: 'Procurar aluno' }), 'Zzz');

    expect(await screen.findByText('Nenhum aluno encontrado para “Zzz”.')).toBeInTheDocument();
  });
});
