import { beforeEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuditTrailScreen } from '../../../src/pages/admin/AuditTrailScreen';
import { pageOf } from '../../support/adminFixtures';
import { renderAsAdministrator, type SimulatedResponse, type SimulatedRoute } from '../../utils';

const TRAIL = 'GET /api/v1/admin/audit-trail';
const INTEGRITY = 'GET /api/v1/admin/audit-trail/integrity';

const NEVER = { status: 'NEVER_VERIFIED', verifiedAt: null, checkedEvents: 0, firstBrokenSequence: null };

function event(overrides: Record<string, unknown> = {}) {
  return {
    id: '0190a000-0000-7000-8000-0000000000f1',
    sequence: 42,
    occurredAt: '2026-09-21T13:00:00Z',
    actorId: '0190a000-0000-7000-8000-0000000000a1',
    actorRole: 'ADMINISTRATOR',
    actorLabel: 'Administrador Local',
    origin: 'WEB',
    operation: 'STUDENT_REGISTER',
    targetType: 'STUDENT',
    targetId: '0190a000-0000-7000-8000-0000000000c1',
    targetLabel: 'Matteo Mafra',
    documentVersion: null,
    itemCount: null,
    result: 'SUCCESS',
    errorCode: null,
    hasOriginHash: false,
    ...overrides,
  };
}

function open(path: string, routes: Record<string, SimulatedResponse | SimulatedRoute> = {}) {
  return renderAsAdministrator([{ path: '/admin/audit', element: <AuditTrailScreen /> }], path, routes);
}

describe('AuditTrailScreen (A14)', () => {
  beforeEach(() => {
    (globalThis as { __testWidth?: number }).__testWidth = 1440;
  });

  it('mostra os eventos com rótulos em português, canal e resultado', async () => {
    open('/admin/audit?from=2026-09-15&to=2026-09-21', {
      [TRAIL]: {
        body: pageOf([
          event(),
          event({
            id: '0190a000-0000-7000-8000-0000000000f2',
            sequence: 43,
            actorRole: 'SYSTEM',
            actorLabel: null,
            origin: 'RETENTION',
            operation: 'RETENTION_RUN',
            targetLabel: null,
            itemCount: 40,
          }),
          event({
            id: '0190a000-0000-7000-8000-0000000000f3',
            sequence: 44,
            operation: 'LESSON_REPORT_APPROVE',
            documentVersion: 2,
            result: 'DENIED',
            errorCode: 'RESOURCE_NOT_FOUND',
            targetLabel: 'Aluno removido · #C1C1C1',
          }),
        ]),
      },
      [INTEGRITY]: { body: NEVER },
    });

    expect(await screen.findByRole('heading', { level: 1, name: 'Trilha de auditoria' })).toBeInTheDocument();
    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(3);
    expect(rows[0]).toHaveTextContent('Aluno cadastrado');
    expect(rows[0]).toHaveTextContent('Administrador Local');
    expect(rows[0]).toHaveTextContent('Web');
    expect(rows[0]).toHaveTextContent('Concluído');
    expect(rows[1]).toHaveTextContent('Sistema');
    expect(rows[1]).toHaveTextContent('Rotina de retenção executada');
    expect(rows[1]).toHaveTextContent('40 itens');
    expect(rows[2]).toHaveTextContent('versão 2');
    expect(rows[2]).toHaveTextContent('Negado');
    expect(rows[2]).toHaveTextContent('Aluno removido · #C1C1C1');
  });

  it('o atalho do aluno preenche o alvo na consulta (três interações no máximo)', async () => {
    const api = open('/admin/audit?targetId=0190a000-0000-7000-8000-0000000000c1', {
      [TRAIL]: { body: pageOf([event()]) },
      [INTEGRITY]: { body: NEVER },
    });

    await screen.findByRole('table');
    expect(api.calls.some((call) => call.url.includes('targetId=0190a000-0000-7000-8000-0000000000c1'))).toBe(true);
  });

  it('sem período na rota, consulta os últimos 7 dias', async () => {
    const api = open('/admin/audit', {
      [TRAIL]: { body: pageOf([]) },
      [INTEGRITY]: { body: NEVER },
    });

    expect(await screen.findByText('Nenhum evento encontrado para estes filtros.')).toBeInTheDocument();
    const call = api.calls.find((item) => item.url.includes('/admin/audit-trail?'));
    const query = new URL(call!.url, 'http://localhost').searchParams;
    const from = Date.parse(`${query.get('from')}T12:00:00Z`);
    const to = Date.parse(`${query.get('to')}T12:00:00Z`);
    expect(Math.round((to - from) / 86_400_000)).toBe(6);
  });

  it('período maior que 90 dias é recusado na tela, sem pedir à API', async () => {
    const api = open('/admin/audit?from=2026-09-15&to=2026-09-21', {
      [TRAIL]: { body: pageOf([event()]) },
      [INTEGRITY]: { body: NEVER },
    });
    await screen.findByRole('table');
    const before = api.calls.filter((item) => item.url.includes('/admin/audit-trail?')).length;

    const from = screen.getByLabelText('De');
    await userEvent.clear(from);
    await userEvent.type(from, '2026-01-01');
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar' }));

    expect(await screen.findByText('O período pode ter no máximo 90 dias.', { selector: '.field__error, .field__error *' })).toBeInTheDocument();
    expect(api.calls.filter((item) => item.url.includes('/admin/audit-trail?')).length).toBe(before);
  });

  it('o selo de integridade tem texto e ícone, e "Verificar agora" mostra a cadeia quebrada', async () => {
    open('/admin/audit?from=2026-09-15&to=2026-09-21', {
      [TRAIL]: { body: pageOf([event()]) },
      [INTEGRITY]: (url: string) =>
        url.includes('verifyNow=true')
          ? { body: { status: 'BROKEN', verifiedAt: '2026-09-21T13:05:00Z', checkedEvents: 120, firstBrokenSequence: 77 } }
          : { body: NEVER },
    } as Record<string, SimulatedResponse | SimulatedRoute>);

    expect(await screen.findByText('Ainda não verificada')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Verificar agora' }));

    const seal = await screen.findByRole('alert');
    expect(seal).toHaveTextContent('Cadeia quebrada');
    expect(seal).toHaveTextContent('120 eventos verificados');
    expect(seal).toHaveTextContent('sequência 77');
  });

  it('recusa de período vinda da API aparece no campo', async () => {
    open('/admin/audit?from=2026-09-15&to=2026-09-21', {
      [TRAIL]: {
        status: 400,
        body: {
          code: 'VALIDATION_FAILED',
          message: 'Dados inválidos.',
          details: { fields: [{ field: 'period', code: 'PERIOD_TOO_LONG' }] },
        },
      },
      [INTEGRITY]: { body: NEVER },
    });

    expect(await screen.findByText('O período pode ter no máximo 90 dias.', { selector: '.field__error, .field__error *' })).toBeInTheDocument();
  });
});
