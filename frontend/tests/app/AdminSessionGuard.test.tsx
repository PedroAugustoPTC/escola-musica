import { describe, expect, it } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { AdminSessionGuard } from '../../src/app/AdminSessionGuard';
import { SessionGuard } from '../../src/app/SessionGuard';
import { ThemeProvider } from '../../src/app/ThemeProvider';
import {
  ADMIN_PASSWORD_CHANGE_REQUIRED_EVENT,
  ADMIN_SESSION_EXPIRED_EVENT,
  SESSION_EXPIRED_EVENT,
} from '../../src/services/apiClient';
import { ADMIN_SESSION_KEY } from '../../src/services/admin/session';
import { mockApi, LIGHT_SESSION } from '../utils';
import { ADMIN_SESSION, ADMIN_SESSION_PASSWORD_CHANGE_REQUIRED } from '../support/adminFixtures';

const routes: RouteObject[] = [
  { path: '/admin/sign-in', element: <p>Tela de entrada</p> },
  { path: '/admin/change-password', element: <p>Tela de troca de senha</p> },
  {
    path: '/admin/teachers',
    element: (
      <AdminSessionGuard>
        <p>Conteúdo administrativo</p>
      </AdminSessionGuard>
    ),
  },
  {
    path: '/teacher-area',
    element: (
      <SessionGuard>
        <p>Conteúdo do professor</p>
      </SessionGuard>
    ),
  },
  { path: '/sign-in', element: <p>Entrada do professor</p> },
];

/** Cliente que mantém os dados (sem coleta) para provar o que o guarda limpa e o que preserva. */
function renderGuard(initialPath: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { retry: false } },
  });
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  render(
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <RouterProvider router={router} />
      </ThemeProvider>
    </QueryClientProvider>,
  );
  return { client, router };
}

const fire = (name: string, detail = 'SESSION_EXPIRED') =>
  act(() => {
    window.dispatchEvent(new CustomEvent(name, { detail }));
  });

describe('AdminSessionGuard', () => {
  it('mostra o conteúdo com sessão de administrador', async () => {
    mockApi({ 'GET /api/v1/admin/session': { body: ADMIN_SESSION } });
    renderGuard('/admin/teachers');
    expect(await screen.findByText('Conteúdo administrativo')).toBeInTheDocument();
  });

  it('sem sessão leva à entrada do administrador guardando o destino', async () => {
    mockApi({
      'GET /api/v1/admin/session': { status: 401, body: { code: 'NOT_AUTHENTICATED', message: 'x' } },
    });
    const { router } = renderGuard('/admin/teachers?search=ana');
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/sign-in'));
    expect(router.state.location.search).toBe('?destination=%2Fadmin%2Fteachers%3Fsearch%3Dana');
  });

  it('com credencial de outro papel (404) mostra "Página não encontrada" sem revelar a área', async () => {
    mockApi({
      'GET /api/v1/admin/session': { status: 404, body: { code: 'RESOURCE_NOT_FOUND', message: 'x' } },
      'GET /api/v1/session': { body: LIGHT_SESSION },
    });
    const { router } = renderGuard('/admin/teachers');
    expect(await screen.findByText('Página não encontrada')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/teachers');
    expect(screen.queryByText('Conteúdo administrativo')).not.toBeInTheDocument();
  });

  it('o evento de sessão expirada do administrador limpa só o cache dele e leva à entrada', async () => {
    mockApi({
      'GET /api/v1/admin/session': { body: ADMIN_SESSION },
      'GET /api/v1/session': { body: LIGHT_SESSION },
    });
    const { client, router } = renderGuard('/admin/teachers?search=ana');
    await screen.findByText('Conteúdo administrativo');
    client.setQueryData(['admin', 'teachers', 'list'], { items: [] });
    client.setQueryData(['session'], LIGHT_SESSION);
    client.setQueryData(['students', 'ana'], { students: [] });

    fire(ADMIN_SESSION_EXPIRED_EVENT);

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/sign-in'));
    expect(router.state.location.search).toBe(
      '?destination=%2Fadmin%2Fteachers%3Fsearch%3Dana&reason=SESSION_EXPIRED',
    );
    expect(client.getQueryData(['admin', 'teachers', 'list'])).toBeUndefined();
    expect(client.getQueryData(ADMIN_SESSION_KEY)).toBeNull();
    expect(client.getQueryData(['session'])).toEqual(LIGHT_SESSION);
    expect(client.getQueryData(['students', 'ana'])).toEqual({ students: [] });
  });

  it('o evento de sessão expirada do professor não afeta o administrador', async () => {
    mockApi({ 'GET /api/v1/admin/session': { body: ADMIN_SESSION } });
    const { router } = renderGuard('/admin/teachers');
    await screen.findByText('Conteúdo administrativo');

    fire(SESSION_EXPIRED_EVENT);

    expect(router.state.location.pathname).toBe('/admin/teachers');
    expect(screen.getByText('Conteúdo administrativo')).toBeInTheDocument();
  });

  it('o evento de sessão expirada do administrador não afeta a área do professor', async () => {
    mockApi({ 'GET /api/v1/session': { body: LIGHT_SESSION } });
    const { router } = renderGuard('/teacher-area');
    await screen.findByText('Conteúdo do professor');

    fire(ADMIN_SESSION_EXPIRED_EVENT);

    expect(router.state.location.pathname).toBe('/teacher-area');
    expect(screen.getByText('Conteúdo do professor')).toBeInTheDocument();
  });

  it('o evento de troca de senha obrigatória leva à tela de troca', async () => {
    mockApi({ 'GET /api/v1/admin/session': { body: ADMIN_SESSION } });
    const { client, router } = renderGuard('/admin/teachers');
    await screen.findByText('Conteúdo administrativo');

    fire(ADMIN_PASSWORD_CHANGE_REQUIRED_EVENT, 'PASSWORD_CHANGE_REQUIRED');

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/change-password'));
    expect(client.getQueryData(ADMIN_SESSION_KEY)).toMatchObject({ mustChangePassword: true });
  });

  it('sessão com senha provisória redireciona qualquer rota administrativa para a troca', async () => {
    mockApi({ 'GET /api/v1/admin/session': { body: ADMIN_SESSION_PASSWORD_CHANGE_REQUIRED } });
    const { router } = renderGuard('/admin/teachers');
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/change-password'));
    expect(screen.queryByText('Conteúdo administrativo')).not.toBeInTheDocument();
  });
});
