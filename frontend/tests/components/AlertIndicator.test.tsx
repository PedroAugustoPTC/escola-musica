import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen } from '@testing-library/react';
import { AlertIndicator } from '../../src/components/AlertIndicator';
import { renderAsAdministrator } from '../utils';

const SUMMARY = 'GET /api/v1/admin/operations/alerts/summary';

function open(count: number) {
  return renderAsAdministrator([{ path: '/admin', element: <AlertIndicator /> }], '/admin', {
    [SUMMARY]: { body: { activeCount: count } },
  });
}

function summaryCalls(calls: { method: string; url: string }[]) {
  return calls.filter((call) => call.url.endsWith('/admin/operations/alerts/summary')).length;
}

function visibility(state: 'visible' | 'hidden') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state });
}

describe('AlertIndicator', () => {
  beforeEach(() => {
    visibility('visible');
  });

  afterEach(() => {
    vi.useRealTimers();
    visibility('visible');
  });

  it('mostra ícone e texto com a contagem, em região aria-live polida', async () => {
    const { container } = open(2);

    const link = await screen.findByRole('link', { name: /2 alertas ativos/ });
    expect(link).toHaveAttribute('href', '/admin/operations/alerts');
    expect(container.querySelector('[aria-live="polite"]')).toContainElement(link);
    expect(link.querySelector('.icon')).not.toBeNull();
  });

  it('usa o singular para um alerta e informa quando não há nenhum', async () => {
    const one = open(1);
    expect(await screen.findByText('1 alerta ativo')).toBeInTheDocument();
    one.unmount();

    open(0);
    expect(await screen.findByText('Sem alertas ativos')).toBeInTheDocument();
  });

  it('não mostra nada, nem erro, quando a consulta falha', async () => {
    const { container } = renderAsAdministrator([{ path: '/admin', element: <AlertIndicator /> }], '/admin', {
      [SUMMARY]: { status: 500, body: { code: 'INTERNAL_ERROR' } },
    });

    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('consulta de novo a cada 60 segundos com a aba visível', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { calls } = open(1);
    await screen.findByText('1 alerta ativo');
    const before = summaryCalls(calls);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });

    expect(summaryCalls(calls)).toBeGreaterThan(before);
  });

  it('não consulta em segundo plano: com a aba oculta o intervalo não dispara', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { calls } = open(1);
    await screen.findByText('1 alerta ativo');
    visibility('hidden');
    const before = summaryCalls(calls);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(180_000);
    });

    expect(summaryCalls(calls)).toBe(before);
  });
});
