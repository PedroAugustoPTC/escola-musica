import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CostsScreen } from '../../../src/pages/admin/CostsScreen';
import { renderAsAdministrator, type SimulatedResponse, type SimulatedRoute } from '../../utils';

const COSTS = 'GET /api/v1/admin/operations/costs';

function aggregate(overrides: Record<string, unknown> = {}) {
  return {
    count: 2,
    totalCostUsd: 0.024,
    averageCostUsd: 0.012,
    totalDurationMs: 12000,
    averageDurationMs: 6000,
    failedCalls: 0,
    ...overrides,
  };
}

function report(overrides: Record<string, unknown> = {}) {
  return {
    month: '2026-09',
    currency: 'USD',
    infrastructureUsd: 12.5,
    grandTotalUsd: 12.744,
    totals: aggregate({ count: 22, totalCostUsd: 0.244, failedCalls: 1 }),
    perAudio: aggregate({ count: 10, totalCostUsd: 0.1, averageCostUsd: 0.01 }),
    perLesson: aggregate({ count: 10, totalCostUsd: 0.12, averageCostUsd: 0.012 }),
    perSemesterReport: aggregate({ count: 2, totalCostUsd: 0.1, averageCostUsd: 0.05 }),
    perStudent: [
      { studentId: '0190a000-0000-7000-8000-0000000000c1', studentLabel: 'Matteo Mafra', ...aggregate({ totalCostUsd: 0.1 }) },
      { studentId: '0190a000-0000-7000-8000-0000000000c2', studentLabel: 'Helena Prado', ...aggregate({ totalCostUsd: 0.3 }) },
      { studentId: '0190a000-0000-7000-8000-0000000000c3', studentLabel: null, ...aggregate({ totalCostUsd: 0.2 }) },
    ],
    byChannel: { WEB: aggregate(), TELEGRAM: aggregate({ totalCostUsd: 0.2 }) },
    byProvider: { 'provider-a/model': aggregate(), unknown: aggregate({ failedCalls: 2 }) },
    ...overrides,
  };
}

function open(path: string, routes: Record<string, SimulatedResponse | SimulatedRoute> = {}) {
  return renderAsAdministrator([{ path: '/admin/operations/costs', element: <CostsScreen /> }], path, routes);
}

describe('CostsScreen (A17)', () => {
  beforeEach(() => {
    (globalThis as { __testWidth?: number }).__testWidth = 1440;
  });

  it('consulta o mês da rota e mostra os cartões em dólar, com total de IA + infraestrutura', async () => {
    const { calls } = open('/admin/operations/costs?month=2026-09', { [COSTS]: { body: report() } });

    expect(await screen.findByRole('heading', { name: 'Tempo e custo do mês' })).toBeInTheDocument();
    expect(await screen.findByText('Total do mês')).toBeInTheDocument();
    expect(screen.getByText(/US\$\s*12,744/)).toBeInTheDocument();
    expect(screen.getByText(/IA US\$\s*0,2440 \+ infraestrutura US\$\s*12,5000/)).toBeInTheDocument();
    expect(screen.getByText('Por áudio')).toBeInTheDocument();
    expect(screen.getByText('Por aula')).toBeInTheDocument();
    expect(screen.getByText('Por relatório semestral')).toBeInTheDocument();
    expect(calls.some((call) => call.url.includes('/admin/operations/costs?month=2026-09'))).toBe(true);
  });

  it('mostra as tabelas por canal, provedor e aluno, com o aluno eliminado como removido', async () => {
    open('/admin/operations/costs?month=2026-09', { [COSTS]: { body: report() } });

    const students = await screen.findByRole('table', { name: 'Por aluno' });
    expect(within(students).getByText('Matteo Mafra')).toBeInTheDocument();
    expect(within(students).getByText('Aluno removido')).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Por canal' })).getByText('Telegram')).toBeInTheDocument();
    const providers = screen.getByRole('table', { name: 'Por provedor' });
    expect(within(providers).getByText('provider-a/model')).toBeInTheDocument();
    expect(within(providers).getByText('Provedor não identificado')).toBeInTheDocument();
  });

  it('ordena a tabela pelo cabeçalho e informa a ordem em aria-sort', async () => {
    open('/admin/operations/costs?month=2026-09', { [COSTS]: { body: report() } });
    const user = userEvent.setup();
    const students = await screen.findByRole('table', { name: 'Por aluno' });

    const names = () =>
      within(students)
        .getAllByRole('rowheader')
        .map((cell) => cell.textContent);
    // padrão: maior custo primeiro
    expect(names()).toEqual(['Helena Prado', 'Aluno removido', 'Matteo Mafra']);
    expect(within(students).getByRole('columnheader', { name: /Custo total/ })).toHaveAttribute(
      'aria-sort',
      'descending',
    );

    await user.click(within(students).getByRole('button', { name: /Ordenar por Nome, crescente/ }));

    expect(names()).toEqual(['Aluno removido', 'Helena Prado', 'Matteo Mafra']);
    expect(within(students).getByRole('columnheader', { name: /Nome/ })).toHaveAttribute('aria-sort', 'ascending');
  });

  it('trocar o mês consulta o mês escolhido', async () => {
    const { calls } = open('/admin/operations/costs?month=2026-09', { [COSTS]: { body: report() } });
    await screen.findByText('Total do mês');

    fireEvent.change(screen.getByLabelText('Mês'), { target: { value: '2026-08' } });

    await screen.findByText('Total do mês');
    expect(calls.some((call) => call.url.includes('month=2026-08'))).toBe(true);
  });

  it('mês sem chamadas mostra o estado vazio e nenhuma tabela', async () => {
    open('/admin/operations/costs?month=2025-01', {
      [COSTS]: {
        body: report({
          infrastructureUsd: 0,
          grandTotalUsd: 0,
          totals: aggregate({ count: 0, totalCostUsd: 0, averageCostUsd: 0, averageDurationMs: 0 }),
          perStudent: [],
          byChannel: {},
          byProvider: {},
        }),
      },
    });

    expect(await screen.findByText('Nenhuma chamada de IA neste mês.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('erro da API mostra o alerta com nova tentativa', async () => {
    open('/admin/operations/costs?month=2026-09', {
      [COSTS]: { status: 500, body: { code: 'INTERNAL_ERROR' } },
    });

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
