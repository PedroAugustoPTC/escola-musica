import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ErasureScreen } from '../../../src/pages/admin/ErasureScreen';
import { renderAsAdministrator } from '../../utils';

const STUDENT_ID = '0190a000-0000-7000-8000-0000000000e1';
const REQUEST_ID = '0190a000-0000-7000-8000-0000000000e2';
const TOKEN = 't'.repeat(40);

const REGISTER = `POST /api/v1/admin/students/${STUDENT_ID}/erasure-requests`;
const PREVIEW = `POST /api/v1/admin/erasure-requests/${REQUEST_ID}/preview`;
const CONFIRM = `POST /api/v1/admin/erasure-requests/${REQUEST_ID}/confirmation`;

function request(status: string) {
  return {
    id: REQUEST_ID,
    studentId: STUDENT_ID,
    status,
    requesterRole: 'MOTHER',
    requestedOn: '2026-11-01',
    receivedVia: 'EMAIL',
    protocolReference: 'PROT-1',
    createdAt: '2026-11-05T12:00:00Z',
  };
}

function previewBody(expiresAt: string) {
  return {
    scope: {
      lessonReports: { APPROVED: 2, CANCELLED: 1 },
      semesterReports: { APPROVED: 1 },
      pdfs: 3,
      assignments: 2,
      consents: 1,
    },
    teachers: [{ id: 'a', name: 'Clara Souza' }],
    confirmationToken: TOKEN,
    expiresAt,
    privacyContact: { name: 'Encarregada', contact: 'dpo@example.test' },
  };
}

function open(path: string, routes: Parameters<typeof renderAsAdministrator>[2]) {
  return renderAsAdministrator(
    [
      { path: '/admin/students/:studentId/erasure', element: <ErasureScreen /> },
      { path: '/admin/erasure-requests/:requestId/run', element: <ErasureScreen /> },
      { path: '/admin/erasure-requests/:requestId/receipt', element: <p>Comprovante aberto</p> },
      { path: '/admin/erasure-requests', element: <p>Lista aberta</p> },
    ],
    path,
    routes,
  );
}

describe('ErasureScreen (A17)', () => {
  beforeEach(() => {
    (globalThis as { __testWidth?: number }).__testWidth = 1440;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('percorre os três passos: pedido, alcance e confirmação com a palavra', async () => {
    const user = userEvent.setup();
    let confirmed: unknown = null;
    open(`/admin/students/${STUDENT_ID}/erasure`, {
      [REGISTER]: { status: 201, body: request('RECEIVED') },
      [PREVIEW]: { body: previewBody(new Date(Date.now() + 600_000).toISOString()) },
      [CONFIRM]: (_url, init) => {
        confirmed = JSON.parse(init.body as string);
        return { body: { ...request('COMPLETED'), completedAt: '2026-11-05T12:01:00Z' } };
      },
    });

    expect(await screen.findByText(/Passo 1 de 3/)).toBeInTheDocument();
    await user.type(screen.getByLabelText('Nome de quem pediu'), 'Maria Guardian');
    await user.type(screen.getByLabelText('Protocolo ou referência'), 'PROT-1');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText(/Passo 3 de 3/)).toBeInTheDocument();
    const scope = screen.getByRole('list', { name: 'O que será eliminado' });
    expect(scope).toHaveTextContent('Relatórios de aula: 3');
    expect(scope).toHaveTextContent('Arquivos PDF: 3');
    expect(screen.getByRole('timer')).toHaveTextContent(/10:0[01]/);

    const confirm = screen.getByRole('button', { name: 'Eliminar dados' });
    expect(confirm).toBeDisabled();
    await user.type(screen.getByLabelText('Digite ELIMINAR para confirmar'), 'eliminar');
    expect(confirm).toBeDisabled();
    await user.clear(screen.getByLabelText('Digite ELIMINAR para confirmar'));
    await user.type(screen.getByLabelText('Digite ELIMINAR para confirmar'), 'ELIMINAR');
    expect(confirm).toBeEnabled();
    await user.click(confirm);

    expect(await screen.findByText('Comprovante aberto')).toBeInTheDocument();
    expect(confirmed).toEqual({ confirmationToken: TOKEN, confirmationWord: 'ELIMINAR' });
  });

  it('a contagem regressiva chega a zero e pede nova prévia', async () => {
    const user = userEvent.setup();
    let previews = 0;
    open(`/admin/erasure-requests/${REQUEST_ID}/run`, {
      [PREVIEW]: () => {
        previews += 1;
        return { body: previewBody(new Date(Date.now() + (previews === 1 ? 1500 : 600_000)).toISOString()) };
      },
    });

    expect(await screen.findByRole('timer')).toBeInTheDocument();
    expect(await screen.findByText(/A confirmação venceu/, undefined, { timeout: 4000 })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Eliminar dados' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Gerar nova prévia' }));
    expect(await screen.findByRole('timer')).toHaveTextContent(/10:0[01]/);
    expect(previews).toBe(2);
  });

  it('com arquivos pendentes mostra o estado e permite repetir', async () => {
    const user = userEvent.setup();
    let previews = 0;
    open(`/admin/erasure-requests/${REQUEST_ID}/run`, {
      [PREVIEW]: () => {
        previews += 1;
        return { body: previewBody(new Date(Date.now() + 600_000).toISOString()) };
      },
      [CONFIRM]: { body: request('IN_PROGRESS') },
    });

    await user.type(await screen.findByLabelText('Digite ELIMINAR para confirmar'), 'ELIMINAR');
    await user.click(screen.getByRole('button', { name: 'Eliminar dados' }));

    expect(await screen.findByText(/alguns arquivos ainda não foram removidos/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Repetir eliminação' }));
    await waitFor(() => expect(previews).toBe(2));
    expect(await screen.findByLabelText('Digite ELIMINAR para confirmar')).toHaveValue('');
  });

  it('pedido recusado mostra o erro do servidor e permanece no passo 1', async () => {
    const user = userEvent.setup();
    open(`/admin/students/${STUDENT_ID}/erasure`, {
      [REGISTER]: { status: 409, body: { code: 'ERASURE_ALREADY_OPEN', message: 'x' } },
    });

    await user.type(await screen.findByLabelText('Nome de quem pediu'), 'Maria Guardian');
    await user.type(screen.getByLabelText('Protocolo ou referência'), 'PROT-1');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText(/Já existe um pedido de eliminação em andamento/)).toBeInTheDocument();
    expect(screen.getByText(/Passo 1 de 3/)).toBeInTheDocument();
    act(() => undefined);
  });
});
