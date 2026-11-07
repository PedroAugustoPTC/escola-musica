import { beforeEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AlertsScreen } from '../../../src/pages/admin/AlertsScreen';
import { renderAsAdministrator, type SimulatedResponse, type SimulatedRoute } from '../../utils';

const ALERTS = 'GET /api/v1/admin/operations/alerts';
const ACK = 'POST /api/v1/admin/operations/alerts/0190a000-0000-7000-8000-0000000000f1/acknowledgement';

const POLICY = {
  providerConsecutiveFailures: { value: 3, source: 'DEFAULT' },
  providerFailureRatePercent: { value: 20, source: 'DEFAULT' },
  providerWindowMinutes: { value: 15, source: 'CONFIGURED' },
  queueMaxAgeMinutes: { value: 5, source: 'DEFAULT' },
  queueMaxItems: { value: 80, source: 'CONFIGURED' },
  storageQuietMinutes: { value: 15, source: 'DEFAULT' },
  evaluationSeconds: { value: 60, source: 'DEFAULT' },
};

function alert(overrides: Record<string, unknown> = {}) {
  return {
    id: '0190a000-0000-7000-8000-0000000000f1',
    type: 'AUDIT_CHAIN_BROKEN',
    component: 'audit-trail',
    detailCode: 'CHAIN_BROKEN',
    status: 'ACTIVE',
    startedAt: '2026-09-21T13:00:00Z',
    lastSeenAt: '2026-09-21T13:05:00Z',
    normalizedAt: null,
    occurrences: 3,
    ...overrides,
  };
}

function page(items: unknown[], extra: Record<string, unknown> = {}) {
  return { items, page: 0, size: 20, total: items.length, effectivePolicy: POLICY, ...extra };
}

function open(path: string, routes: Record<string, SimulatedResponse | SimulatedRoute> = {}) {
  return renderAsAdministrator([{ path: '/admin/operations/alerts', element: <AlertsScreen /> }], path, routes);
}

describe('AlertsScreen (A18)', () => {
  beforeEach(() => {
    (globalThis as { __testWidth?: number }).__testWidth = 1440;
  });

  it('mostra os ativos no topo e o histórico abaixo, com o tipo em português', async () => {
    open('/admin/operations/alerts', {
      [ALERTS]: {
        body: page([
          alert(),
          alert({
            id: '0190a000-0000-7000-8000-0000000000f2',
            type: 'STORAGE_FAILED',
            component: 'pdf-storage',
            status: 'NORMALIZED',
            normalizedAt: '2026-09-21T14:00:00Z',
            occurrences: 2,
          }),
        ]),
      },
    });

    const active = await screen.findByRole('table', { name: 'Alertas ativos' });
    expect(within(active).getByText('Cadeia da trilha de auditoria quebrada')).toBeInTheDocument();
    expect(within(active).getByText('audit-trail')).toBeInTheDocument();
    expect(within(active).getByText('Ativo')).toBeInTheDocument();
    const history = screen.getByRole('table', { name: 'Histórico' });
    expect(within(history).getByText('Falha no armazenamento de PDFs')).toBeInTheDocument();
    expect(within(history).getByText('Normalizado')).toBeInTheDocument();
    const headings = screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent);
    expect(headings.indexOf('Alertas ativos')).toBeLessThan(headings.indexOf('Histórico'));
  });

  it('mostra os prazos e limites efetivos com a origem de cada um', async () => {
    open('/admin/operations/alerts', { [ALERTS]: { body: page([]) } });

    expect(await screen.findByText('Prazos e limites em vigor')).toBeInTheDocument();
    expect(screen.getByText('Itens máximos na fila')).toBeInTheDocument();
    expect(screen.getByText('80 (configurado)')).toBeInTheDocument();
    expect(screen.getAllByText('3 (padrão)').length).toBeGreaterThan(0);
  });

  it('sem alertas mostra os dois estados vazios', async () => {
    open('/admin/operations/alerts', { [ALERTS]: { body: page([]) } });

    expect(await screen.findByText('Nenhum alerta ativo.')).toBeInTheDocument();
    expect(screen.getByText('Ainda não há alertas normalizados.')).toBeInTheDocument();
  });

  it('só a quebra da trilha tem "Marcar como apurado" e a ação chama o reconhecimento', async () => {
    const { calls } = open('/admin/operations/alerts', {
      [ALERTS]: {
        body: page([
          alert(),
          alert({
            id: '0190a000-0000-7000-8000-0000000000f3',
            type: 'RETENTION_FAILED',
            component: 'retention',
          }),
        ]),
      },
      [ACK]: { body: alert({ status: 'NORMALIZED', normalizedAt: '2026-09-21T15:00:00Z' }) },
    });
    const user = userEvent.setup();
    await screen.findByRole('table', { name: 'Alertas ativos' });

    const buttons = screen.getAllByRole('button', { name: 'Marcar como apurado' });
    expect(buttons).toHaveLength(1);
    await user.click(buttons[0]!);

    expect(await screen.findByText('Alerta marcado como apurado.')).toBeInTheDocument();
    expect(calls.some((call) => call.method === 'POST' && call.url.endsWith('/acknowledgement'))).toBe(true);
  });

  it('erro da API mostra o alerta de erro', async () => {
    open('/admin/operations/alerts', { [ALERTS]: { status: 500, body: { code: 'INTERNAL_ERROR' } } });

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
