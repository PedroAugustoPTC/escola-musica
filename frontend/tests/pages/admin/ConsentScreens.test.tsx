import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConsentTermsScreen } from '../../../src/pages/admin/ConsentTermsScreen';
import { PendingConsentsScreen } from '../../../src/pages/admin/PendingConsentsScreen';
import { renderAsAdministrator, type SimulatedResponse, type SimulatedRoute } from '../../utils';

const PENDING = 'GET /api/v1/admin/consents/pending';
const TERMS = 'GET /api/v1/admin/consent-terms';
const PUBLISH = 'POST /api/v1/admin/consent-terms';

const STUDENT_ID = '0190a000-0000-7000-8000-0000000000c1';

function term(number: number, overrides: Record<string, unknown> = {}) {
  return {
    number,
    publishedOn: '2026-09-01',
    text: `Institutional text ${number}`,
    requiresNewConsent: false,
    current: false,
    ...overrides,
  };
}

function openPending(routes: Record<string, SimulatedResponse | SimulatedRoute>) {
  return renderAsAdministrator(
    [{ path: '/admin/consents/pending', element: <PendingConsentsScreen /> }],
    '/admin/consents/pending',
    routes,
  );
}

function openTerms(routes: Record<string, SimulatedResponse | SimulatedRoute>) {
  return renderAsAdministrator(
    [{ path: '/admin/consent-terms', element: <ConsentTermsScreen /> }],
    '/admin/consent-terms',
    routes,
  );
}

describe('PendingConsentsScreen (A16c)', () => {
  beforeEach(() => {
    (globalThis as { __testWidth?: number }).__testWidth = 1440;
  });

  it('em operação normal a lista fica vazia e diz que não há pendência', async () => {
    openPending({ [PENDING]: { body: { items: [], page: 0, size: 20, total: 0 } } });

    expect(await screen.findByRole('heading', { level: 1, name: 'Consentimentos pendentes' })).toBeInTheDocument();
    expect(await screen.findByText('Nenhuma pendência')).toBeInTheDocument();
  });

  it('lista o aluno com o motivo e o link para o detalhe', async () => {
    openPending({
      [PENDING]: {
        body: {
          items: [
            { studentId: STUDENT_ID, studentName: 'Matteo Mafra', reason: 'REVOKED' },
            { studentId: '0190a000-0000-7000-8000-0000000000c2', studentName: 'Lia Souza', reason: 'SUPERSEDED' },
          ],
          page: 0,
          size: 20,
          total: 2,
        },
      },
    });

    const link = await screen.findByRole('link', { name: 'Matteo Mafra' });
    expect(link).toHaveAttribute('href', `/admin/students/${STUDENT_ID}`);
    expect(screen.getByText('Consentimento revogado')).toBeInTheDocument();
    expect(screen.getByText('Termo novo exige novo consentimento')).toBeInTheDocument();
  });
});

describe('ConsentTermsScreen (A16d)', () => {
  beforeEach(() => {
    (globalThis as { __testWidth?: number }).__testWidth = 1440;
  });

  it('mostra as versões, da mais recente à mais antiga, e marca a vigente', async () => {
    openTerms({
      [TERMS]: {
        body: [term(2, { current: true, requiresNewConsent: true }), term(1)],
      },
    });

    expect(await screen.findByText(/Versão 2 · 01\/09\/2026 · vigente · exige novo consentimento/)).toBeInTheDocument();
    expect(screen.getByText(/Versão 1 · 01\/09\/2026$/)).toBeInTheDocument();
  });

  it('publicar pede confirmação que explica o efeito antes de enviar', async () => {
    const user = userEvent.setup();
    let published: unknown = null;
    openTerms({
      [TERMS]: { body: [term(1, { current: true })] },
      'GET /api/v1/admin/consent-terms/impact': { body: { studentsAffected: 3 } },
      [PUBLISH]: (_url, init) => {
        published = JSON.parse(init.body as string);
        return { status: 201, body: term(2, { current: true, requiresNewConsent: true }) };
      },
    });

    await user.type(await screen.findByLabelText('Texto do termo'), 'New institutional text');
    await user.click(screen.getByRole('checkbox', { name: /Exige novo consentimento/ }));
    await user.click(screen.getByRole('button', { name: 'Publicar versão' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('deixam de valer');
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('3 alunos ficarão pendentes'));
    expect(published).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Confirmar publicação' }));

    await screen.findByRole('button', { name: 'Publicar versão' });
    expect(published).toMatchObject({ text: 'New institutional text', requiresNewConsent: true });
  });

  it('versão compatível avisa que os consentimentos continuam válidos', async () => {
    const user = userEvent.setup();
    openTerms({ [TERMS]: { body: [term(1, { current: true })] } });

    await user.type(await screen.findByLabelText('Texto do termo'), 'Text');
    await user.click(screen.getByRole('button', { name: 'Publicar versão' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('continuam válidos');
  });
});
