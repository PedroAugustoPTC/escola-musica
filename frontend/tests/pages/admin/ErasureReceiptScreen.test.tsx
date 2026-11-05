import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { ErasureReceiptScreen } from '../../../src/pages/admin/ErasureReceiptScreen';
import { renderAsAdministrator } from '../../utils';

const REQUEST_ID = '0190a000-0000-7000-8000-0000000000e2';
const RECEIPT = `GET /api/v1/admin/erasure-requests/${REQUEST_ID}/receipt`;

const receipt = {
  requestId: REQUEST_ID,
  studentShortId: 'e1e2e3',
  protocolReference: 'PROT-2026/01',
  requestedOn: '2026-11-01',
  completedAt: '2026-11-05T12:01:00Z',
  scope: {
    lessonReports: { APPROVED: 2 },
    semesterReports: { APPROVED: 1 },
    pdfs: 3,
    assignments: 2,
    consents: 1,
  },
  privacyContact: { name: 'Encarregada', contact: 'dpo@example.test' },
};

describe('ErasureReceiptScreen (A17b)', () => {
  beforeEach(() => {
    (globalThis as { __testWidth?: number }).__testWidth = 1440;
  });

  it('mostra protocolo, datas e alcance, sem nome nem dado do aluno', async () => {
    renderAsAdministrator(
      [{ path: '/admin/erasure-requests/:requestId/receipt', element: <ErasureReceiptScreen /> }],
      `/admin/erasure-requests/${REQUEST_ID}/receipt`,
      { [RECEIPT]: { body: receipt } },
    );

    expect(await screen.findByRole('heading', { level: 1, name: 'Comprovante de eliminação de dados' })).toBeInTheDocument();
    expect(screen.getByText('PROT-2026/01')).toBeInTheDocument();
    expect(screen.getByText('#E1E2E3')).toBeInTheDocument();
    expect(screen.getByText('01/11/2026')).toBeInTheDocument();
    const text = document.body.textContent ?? '';
    expect(text).not.toMatch(/CPF/);
    expect(text).not.toContain('Maria Guardian');
  });

  it('o botão imprime a página', async () => {
    const user = userEvent.setup();
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    renderAsAdministrator(
      [{ path: '/admin/erasure-requests/:requestId/receipt', element: <ErasureReceiptScreen /> }],
      `/admin/erasure-requests/${REQUEST_ID}/receipt`,
      { [RECEIPT]: { body: receipt } },
    );

    await user.click(await screen.findByRole('button', { name: 'Imprimir' }));
    expect(print).toHaveBeenCalledOnce();
  });

  it('a folha de estilo esconde a navegação e o botão na impressão', () => {
    const css = readFileSync(join(process.cwd(), 'src/styles/admin.css'), 'utf8');
    expect(css).toMatch(/@media print[\s\S]*\.erasure-receipt__actions/);
  });
});
