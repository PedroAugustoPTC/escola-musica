import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderAdminArea } from './adminTestSupport';

const totalOf = (total: number) => ({ body: { items: [], page: 0, size: 1, total } });

describe('AdminHomeScreen (A04)', () => {
  it('mostra os atalhos com as contagens de ativos', async () => {
    renderAdminArea('/admin', {
      'GET /api/v1/admin/teachers': totalOf(7),
      'GET /api/v1/admin/students': totalOf(42),
      'GET /api/v1/admin/administrators': totalOf(2),
    });

    expect(await screen.findByRole('heading', { name: 'Início' })).toBeInTheDocument();
    expect(await screen.findByText('42')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Cadastrar professor' })).toHaveAttribute(
      'href',
      '/admin/teachers/new',
    );
    expect(screen.getByRole('link', { name: 'Cadastrar aluno' })).toHaveAttribute(
      'href',
      '/admin/students/new',
    );
    expect(screen.getByText('Olá, Marina Duarte.')).toBeInTheDocument();
  });

  it('oferece o atalho para o histórico da escola', async () => {
    renderAdminArea('/admin', {
      'GET /api/v1/admin/teachers': totalOf(7),
      'GET /api/v1/admin/students': totalOf(42),
      'GET /api/v1/admin/administrators': totalOf(2),
    });

    expect(await screen.findByRole('link', { name: 'Ver histórico' })).toHaveAttribute(
      'href',
      '/admin/history',
    );
  });

  it('falha ao carregar mostra o erro com nova tentativa', async () => {
    renderAdminArea('/admin', {
      'GET /api/v1/admin/teachers': { status: 500, body: { code: 'INTERNAL_ERROR', message: 'Falhou.' } },
      'GET /api/v1/admin/students': totalOf(1),
      'GET /api/v1/admin/administrators': totalOf(1),
    });
    expect(await screen.findByRole('alert')).toHaveTextContent('Falhou.');
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument();
  });
});
