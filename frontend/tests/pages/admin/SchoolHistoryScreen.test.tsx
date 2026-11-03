import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SchoolHistoryScreen } from '../../../src/pages/admin/SchoolHistoryScreen';
import {
  TEACHER_ACTIVE,
  TEACHER_INACTIVE,
  TEST_CPF,
  administratorStudent,
  pageOf,
  validationError,
} from '../../support/adminFixtures';
import {
  HISTORY_STUDENT,
  HISTORY_TEACHER_INACTIVE,
  schoolHistoryItem,
  schoolHistoryPage,
} from '../../support/schoolHistoryFixtures';
import { renderAsAdministrator, type SimulatedResponse, type SimulatedRoute } from '../../utils';

const HISTORY = 'GET /api/v1/admin/school-history';
const STUDENTS = 'GET /api/v1/admin/students';
const TEACHERS = 'GET /api/v1/admin/teachers';

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

function open(path: string, routes: Record<string, SimulatedResponse | SimulatedRoute> = {}) {
  return renderAsAdministrator([{ path: '/admin/history', element: <SchoolHistoryScreen /> }], path, routes);
}

describe('SchoolHistoryScreen (A13)', () => {
  beforeEach(() => {
    (globalThis as { __testWidth?: number }).__testWidth = 1440;
  });

  it('com aluno na rota mostra o cabeçalho do aluno e a lista, sem a coluna Aluno', async () => {
    const api = open(`/admin/history?studentId=${HISTORY_STUDENT.id}`, {
      [HISTORY]: { body: schoolHistoryPage([LESSON, SEMESTER], { student: HISTORY_STUDENT }) },
    });

    expect(await screen.findByRole('heading', { level: 1, name: 'Histórico' })).toBeInTheDocument();
    expect(screen.getByText('Relatórios aprovados da escola')).toBeInTheDocument();
    expect(await screen.findByText('Aula de 10/03/2026')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Matteo Mafra' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Trocar aluno' })).toBeInTheDocument();

    const table = screen.getByRole('table');
    const headers = within(table)
      .getAllByRole('columnheader')
      .map((cell) => cell.textContent);
    expect(headers).toEqual(['Relatório', 'Professor', 'Aprovado em', 'Versão', 'PDF']);
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('Clara Souza');
    expect(rows[0]).toHaveTextContent('10/03/2026 18:30');
    expect(rows[0]).toHaveTextContent('v3');
    expect(rows[1]).toHaveTextContent('Bruno Lima');
    expect(rows[1]).toHaveTextContent('Inativo');
    expect(rows[1]).toHaveTextContent('v1');
    expect(api.calls.some((call) => call.url.includes(`studentId=${HISTORY_STUDENT.id}`))).toBe(true);
    expect(screen.getByText('2 relatórios aprovados')).toBeInTheDocument();
  });

  it('sem aluno na rota mostra a busca de aluno e a coluna Aluno', async () => {
    open('/admin/history', {
      [HISTORY]: { body: schoolHistoryPage([LESSON]) },
    });

    expect(await screen.findByRole('combobox', { name: 'Procurar aluno' })).toBeInTheDocument();
    const table = await screen.findByRole('table');
    expect(within(table).getAllByRole('columnheader').map((cell) => cell.textContent)).toContain('Aluno');
    expect(within(table).getByText('Matteo Mafra')).toBeInTheDocument();
  });

  it('escolher o aluno fixa só o identificador na rota e refaz a lista', async () => {
    const student = administratorStudent();
    const api = open('/admin/history', {
      [HISTORY]: (url) => ({
        body: url.includes('studentId=')
          ? schoolHistoryPage([LESSON], { student: HISTORY_STUDENT })
          : schoolHistoryPage([LESSON, SEMESTER]),
      }),
      [STUDENTS]: { body: pageOf([student]) },
    });
    const user = userEvent.setup();

    await user.type(await screen.findByRole('combobox', { name: 'Procurar aluno' }), 'Matt');
    await user.click(await screen.findByRole('option', { name: /Matteo Mafra/ }));

    await waitFor(() => expect(api.router.state.location.search).toBe(`?studentId=${student.id}`));
    expect(await screen.findByRole('button', { name: 'Trocar aluno' })).toBeInTheDocument();
    const search = api.calls.find((call) => call.url.includes('/admin/students'))!;
    expect(search.url).toContain('status=ALL');
  });

  it('"Trocar aluno" tira o filtro de aluno da rota', async () => {
    const api = open(`/admin/history?studentId=${HISTORY_STUDENT.id}`, {
      [HISTORY]: { body: schoolHistoryPage([LESSON], { student: HISTORY_STUDENT }) },
    });
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Trocar aluno' }));

    expect(api.router.state.location.search).toBe('');
    expect(await screen.findByRole('combobox', { name: 'Procurar aluno' })).toBeInTheDocument();
  });

  it('aluno sem aprovados mostra o estado vazio do aluno', async () => {
    open(`/admin/history?studentId=${HISTORY_STUDENT.id}`, {
      [HISTORY]: { body: schoolHistoryPage([], { student: HISTORY_STUDENT }) },
    });

    expect(await screen.findByText('Nenhum relatório aprovado encontrado para este aluno.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('mostra carregando e depois a falha com o código de referência e "Tentar novamente"', async () => {
    open(`/admin/history?studentId=${HISTORY_STUDENT.id}`, {
      [HISTORY]: {
        status: 500,
        body: {
          code: 'INTERNAL_ERROR',
          message: 'Ocorreu um erro inesperado. Código de referência: REF-4F2A.',
          reference: 'REF-4F2A',
        },
      },
    });

    expect(screen.getByRole('status', { name: 'Carregando histórico' })).toBeInTheDocument();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('REF-4F2A');
    expect(within(alert).getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument();
  });

  it('?search= com forma de CPF é descartado: a rota é limpa e a API de alunos não é chamada', async () => {
    const api = open(`/admin/history?search=${TEST_CPF}`, {
      [HISTORY]: { body: schoolHistoryPage([LESSON]) },
      [STUDENTS]: { body: pageOf([administratorStudent()]) },
    });

    expect(
      await screen.findByText('Não há busca por CPF. Procure pelo nome ou pelo instrumento.'),
    ).toBeInTheDocument();
    await waitFor(() => expect(api.router.state.location.search).toBe(''));
    expect(api.calls.some((call) => call.url.includes('/admin/students'))).toBe(false);
    expect(api.calls.some((call) => call.url.includes(TEST_CPF))).toBe(false);
  });

  it('não oferece nenhuma ação de escrita sobre o relatório', async () => {
    open(`/admin/history?studentId=${HISTORY_STUDENT.id}`, {
      [HISTORY]: { body: schoolHistoryPage([LESSON], { student: HISTORY_STUDENT }) },
    });
    await screen.findByText('Aula de 10/03/2026');

    for (const name of [/Aprovar/, /Alterar/, /Cancelar/, /Gerar/, /Enviar/]) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
  });

  describe('filtros e paginação', () => {
    const teachers = { [TEACHERS]: { body: pageOf([TEACHER_ACTIVE, TEACHER_INACTIVE]) } };

    it('lista os professores (inativo marcado) e aplica os filtros na rota com replace, só com identificadores', async () => {
      const api = open('/admin/history', {
        ...teachers,
        [HISTORY]: { body: schoolHistoryPage([LESSON, SEMESTER]) },
      });
      const user = userEvent.setup();

      const teacher = await screen.findByRole('combobox', { name: 'Professor' });
      await waitFor(() => expect(within(teacher).getAllByRole('option')).toHaveLength(3));
      expect(within(teacher).getByRole('option', { name: 'Bruno Lima (inativo)' })).toBeInTheDocument();
      await user.selectOptions(teacher, TEACHER_INACTIVE.id);
      await user.selectOptions(screen.getByRole('combobox', { name: 'Tipo' }), 'SEMESTER');
      await user.type(screen.getByLabelText('De'), '2026-07-01');
      await user.type(screen.getByLabelText('Até'), '2026-12-31');
      expect(api.router.state.location.search).toBe('');
      await user.click(screen.getByRole('button', { name: 'Aplicar' }));

      await waitFor(() =>
        expect(api.router.state.location.search).toBe(
          `?teacherId=${TEACHER_INACTIVE.id}&type=SEMESTER&from=2026-07-01&to=2026-12-31`,
        ),
      );
      expect(api.router.state.historyAction).toBe('REPLACE');
      expect(api.router.state.location.search).not.toContain('Bruno');
      await waitFor(() =>
        expect(
          api.calls.some(
            (call) =>
              call.url.includes(`teacherId=${TEACHER_INACTIVE.id}`) &&
              call.url.includes('type=SEMESTER') &&
              call.url.includes('from=2026-07-01') &&
              call.url.includes('to=2026-12-31'),
          ),
        ).toBe(true),
      );
    });

    it('mostra os filtros da rota nos campos', async () => {
      open(`/admin/history?teacherId=${TEACHER_ACTIVE.id}&type=LESSON&from=2026-03-01`, {
        ...teachers,
        [HISTORY]: { body: schoolHistoryPage([LESSON]) },
      });

      await waitFor(() =>
        expect(screen.getByRole('combobox', { name: 'Professor' })).toHaveValue(TEACHER_ACTIVE.id),
      );
      expect(screen.getByRole('combobox', { name: 'Tipo' })).toHaveValue('LESSON');
      expect(screen.getByLabelText('De')).toHaveValue('2026-03-01');
      expect(screen.getByText('3 filtros ativos')).toBeInTheDocument();
    });

    it('"Limpar filtros" tira os filtros da rota e mantém o aluno', async () => {
      const api = open(`/admin/history?studentId=${HISTORY_STUDENT.id}&type=LESSON&from=2026-03-01`, {
        ...teachers,
        [HISTORY]: { body: schoolHistoryPage([LESSON], { student: HISTORY_STUDENT }) },
      });
      const user = userEvent.setup();

      await screen.findByText('Aula de 10/03/2026');
      await user.click(screen.getAllByRole('button', { name: 'Limpar filtros' })[0]!);

      await waitFor(() =>
        expect(api.router.state.location.search).toBe(`?studentId=${HISTORY_STUDENT.id}`),
      );
    });

    it('filtros sem resultado mostram a mensagem própria com "Limpar filtros"', async () => {
      open('/admin/history?type=SEMESTER', {
        ...teachers,
        [HISTORY]: { body: schoolHistoryPage([]) },
      });

      expect(
        await screen.findByText('Nenhum relatório aprovado encontrado para estes filtros.'),
      ).toBeInTheDocument();
      expect(screen.getAllByRole('button', { name: 'Limpar filtros' })).toHaveLength(2);
    });

    it('erro de validação do servidor aparece junto do campo, sem alerta geral nem lista', async () => {
      open('/admin/history?from=2026-13-45', {
        ...teachers,
        [HISTORY]: { status: 400, body: validationError([{ field: 'from', code: 'INVALID_DATE' }]) },
      });

      const from = await screen.findByLabelText('De');
      await waitFor(() => expect(from).toHaveAttribute('aria-invalid', 'true'));
      expect(from).toHaveAccessibleDescription('Data inválida. Use o formato dd/mm/aaaa.');
      expect(screen.queryByRole('button', { name: 'Tentar novamente' })).not.toBeInTheDocument();
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });

    it('pagina com "1–20 de N", muda a página na rota e leva o foco ao início da lista', async () => {
      const items = Array.from({ length: 20 }, (_, index) =>
        schoolHistoryItem({
          reportId: `0190a000-0000-7000-8000-00000000f${String(index).padStart(3, '0')}`,
        }),
      );
      const api = open('/admin/history', {
        ...teachers,
        [HISTORY]: (url) => ({
          body: url.includes('page=1')
            ? schoolHistoryPage([LESSON], { page: 1, total: 21 })
            : schoolHistoryPage(items, { total: 21 }),
        }),
      });
      const user = userEvent.setup();

      expect(await screen.findByText('1–20 de 21')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
      await user.click(screen.getByRole('button', { name: 'Próxima' }));

      expect(await screen.findByText('21–21 de 21')).toBeInTheDocument();
      expect(api.router.state.location.search).toBe('?page=1');
      expect(api.router.state.historyAction).toBe('REPLACE');
      await waitFor(() => expect(document.querySelector('.school-history__start')).toHaveFocus());
      expect(screen.getByRole('button', { name: 'Próxima' })).toBeDisabled();

      await user.click(screen.getByRole('button', { name: 'Anterior' }));
      await waitFor(() => expect(api.router.state.location.search).toBe(''));
    });

    it('mudar um filtro volta à primeira página', async () => {
      const api = open('/admin/history?page=1', {
        ...teachers,
        [HISTORY]: { body: schoolHistoryPage([LESSON], { page: 1, total: 21 }) },
      });
      const user = userEvent.setup();

      await user.selectOptions(await screen.findByRole('combobox', { name: 'Tipo' }), 'LESSON');
      await user.click(screen.getByRole('button', { name: 'Aplicar' }));

      await waitFor(() => expect(api.router.state.location.search).toBe('?type=LESSON'));
    });
  });
});
