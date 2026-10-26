import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { RouteObject } from 'react-router';
import { AdminLayout } from '../../../src/app/AdminLayout';
import { AdminSessionGuard } from '../../../src/app/AdminSessionGuard';
import { renderRoutes, mockApi } from '../../utils';
import { LIVE_ADMIN_SESSION as ADMIN_SESSION } from '../../pages/admin/adminTestSupport';

const routes: RouteObject[] = [
  { path: '/admin/sign-in', element: <p>Entrada do administrador</p> },
  {
    element: (
      <AdminSessionGuard>
        <AdminLayout />
      </AdminSessionGuard>
    ),
    children: [
      {
        path: '/admin',
        element: (
          <>
            <h1>Início</h1>
            <label>
              Rascunho
              <input />
            </label>
          </>
        ),
      },
      { path: '/admin/teachers', element: <h1>Professores da escola</h1> },
    ],
  },
];

const inMinutes = (minutes: number, extraMs = 0) =>
  new Date(Date.now() + minutes * 60_000 + extraMs).toISOString();

describe('AdminLayout', () => {
  it('mostra o selo "Área administrativa", a navegação e o horário de expiração', async () => {
    mockApi({ 'GET /api/v1/admin/session': { body: ADMIN_SESSION } });
    renderRoutes(routes, '/admin');

    expect(await screen.findByRole('heading', { name: 'Início' })).toBeInTheDocument();
    expect(screen.getByText('Área administrativa')).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Área administrativa' });
    const links = within(nav).getAllByRole('link');
    expect(links).toHaveLength(3);
    ['Professores', 'Alunos', 'Administradores'].forEach((name, index) =>
      expect(links[index]).toHaveAccessibleName(name),
    );
    expect(within(nav).getByRole('link', { name: 'Professores' })).toHaveAttribute('href', '/admin/teachers');
    expect(screen.getByText('Sua sessão expira às 11:00.')).toBeInTheDocument();
    expect(screen.getByText('Marina Duarte')).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveAttribute('id', 'content');
    expect(screen.getByRole('link', { name: 'Ir para o conteúdo' })).toHaveAttribute('href', '#content');
  });

  it('marca a página atual na navegação', async () => {
    mockApi({ 'GET /api/v1/admin/session': { body: ADMIN_SESSION } });
    renderRoutes(routes, '/admin/teachers');
    await screen.findByRole('heading', { name: 'Professores da escola' });
    expect(screen.getByRole('link', { name: 'Professores' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Alunos' })).not.toHaveAttribute('aria-current');
  });

  it('no computador mantém selo, navegação e saída', async () => {
    (globalThis as { __testWidth?: number }).__testWidth = 1440;
    mockApi({ 'GET /api/v1/admin/session': { body: ADMIN_SESSION } });
    renderRoutes(routes, '/admin');
    await screen.findByRole('heading', { name: 'Início' });
    expect(screen.getByText('Área administrativa')).toBeInTheDocument();
    expect(screen.getAllByRole('navigation')).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Sair' })).toBeInTheDocument();
  });

  it('alterna o tema claro/escuro', async () => {
    mockApi({ 'GET /api/v1/admin/session': { body: ADMIN_SESSION } });
    const user = userEvent.setup();
    renderRoutes(routes, '/admin');
    await screen.findByRole('heading', { name: 'Início' });
    const toggle = screen.getByRole('button', { name: 'Modo escuro' });
    const before = toggle.getAttribute('aria-pressed');
    await user.click(toggle);
    expect(screen.getByRole('button', { name: 'Modo escuro' }).getAttribute('aria-pressed')).not.toBe(before);
  });

  it('"Sair" encerra a sessão no servidor e só então volta à entrada', async () => {
    let ended = false;
    const api = mockApi({
      'GET /api/v1/admin/session': () =>
        ended ? { status: 401, body: { code: 'NOT_AUTHENTICATED', message: 'x' } } : { body: ADMIN_SESSION },
      'DELETE /api/v1/admin/session': () => {
        ended = true;
        return { status: 204 };
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, '/admin');
    await screen.findByRole('heading', { name: 'Início' });
    await user.click(screen.getByRole('button', { name: 'Sair' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/sign-in'));
    expect(api.calls.some((call) => call.method === 'DELETE' && call.url === '/api/v1/admin/session')).toBe(
      true,
    );
  });

  it('"Sair" que falha não sai da tela e mostra o erro', async () => {
    mockApi({
      'GET /api/v1/admin/session': { body: ADMIN_SESSION },
      'DELETE /api/v1/admin/session': {
        status: 500,
        body: { code: 'INTERNAL_ERROR', message: 'Ocorreu um erro inesperado.' },
      },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, '/admin');
    await screen.findByRole('heading', { name: 'Início' });
    await user.click(screen.getByRole('button', { name: 'Sair' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Ocorreu um erro inesperado.');
    expect(router.state.location.pathname).toBe('/admin');
  });

  it('avisa com role="alert" 5 minutos antes de expirar', async () => {
    mockApi({
      'GET /api/v1/admin/session': { body: { ...ADMIN_SESSION, expiresAt: inMinutes(5, 1200) } },
    });
    renderRoutes(routes, '/admin');
    await screen.findByRole('heading', { name: 'Início' });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(await screen.findByRole('alert', {}, { timeout: 4000 })).toHaveTextContent(
      'Sua sessão termina em 5 minuto(s)',
    );
  });

  it('ao expirar volta à entrada guardando o destino e não mantém o que foi digitado', async () => {
    mockApi({
      'GET /api/v1/admin/session': { body: { ...ADMIN_SESSION, expiresAt: inMinutes(0, 2500) } },
    });
    const user = userEvent.setup();
    const { router } = renderRoutes(routes, '/admin');
    await user.type(await screen.findByLabelText('Rascunho'), 'texto digitado');
    expect(screen.getByLabelText('Rascunho')).toHaveValue('texto digitado');

    expect(await screen.findByText('Entrada do administrador', {}, { timeout: 6000 })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/sign-in');
    expect(router.state.location.search).toContain('reason=SESSION_EXPIRED');
    expect(screen.queryByLabelText('Rascunho')).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain('texto digitado');
  });

  it('ao trocar de rota o foco vai para o título da nova página; sem mudar de rota ele fica onde está', async () => {
    mockApi({ 'GET /api/v1/admin/session': { body: ADMIN_SESSION } });
    const user = userEvent.setup();
    renderRoutes(routes, '/admin');
    await screen.findByRole('heading', { name: 'Início' });
    // a primeira carga não rouba o foco do campo que o usuário alcançar primeiro
    expect(screen.getByRole('heading', { name: 'Início' })).not.toHaveFocus();
    await user.click(screen.getByLabelText('Rascunho'));
    expect(screen.getByLabelText('Rascunho')).toHaveFocus();

    await user.click(screen.getByRole('link', { name: 'Professores' }));

    const heading = await screen.findByRole('heading', { name: 'Professores da escola' });
    await waitFor(() => expect(heading).toHaveFocus());
  });
});
