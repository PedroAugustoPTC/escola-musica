import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TEACHER_ACCESS_CODE, TEACHER_ACTIVE, TEACHER_INACTIVE, pageOf } from '../../support/adminFixtures';
import { renderAdminArea } from './adminTestSupport';

const LIST = 'GET /api/v1/admin/teachers';

describe('TeachersScreen (A05)', () => {
  it('lista os professores com situação em ícone e texto e "Telegram vinculado", sem código', async () => {
    renderAdminArea('/admin/teachers', {
      [LIST]: { body: pageOf([TEACHER_ACTIVE, TEACHER_INACTIVE]) },
    });

    expect(await screen.findByRole('heading', { name: 'Professores' })).toBeInTheDocument();
    const items = await screen.findAllByRole('listitem');
    const active = items.find((item) => within(item).queryByText('Clara Souza'))!;
    const inactive = items.find((item) => within(item).queryByText('Bruno Lima'))!;
    expect(within(active).getByRole('link', { name: 'Clara Souza' })).toHaveAttribute(
      'href',
      `/admin/teachers/${TEACHER_ACTIVE.id}`,
    );
    expect(within(active).getByText('clara@example.test')).toBeInTheDocument();
    expect(within(active).getByText('Ativo')).toBeInTheDocument();
    expect(within(active).getByText('Telegram vinculado')).toBeInTheDocument();
    expect(within(inactive).getByText('Inativo')).toBeInTheDocument();
    expect(within(inactive).queryByText('Telegram vinculado')).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain(TEACHER_ACCESS_CODE);
    expect(screen.getByRole('link', { name: 'Cadastrar professor' })).toHaveAttribute(
      'href',
      '/admin/teachers/new',
    );
  });

  it('busca em <form role="search"> e guarda o termo na URL', async () => {
    const api = renderAdminArea('/admin/teachers', { [LIST]: { body: pageOf([TEACHER_ACTIVE]) } });
    const user = userEvent.setup();
    await screen.findByText('Clara Souza');

    const form = screen.getByRole('search');
    await user.type(within(form).getByRole('searchbox', { name: 'Buscar professor' }), 'clara');
    await user.click(within(form).getByRole('button', { name: 'Buscar' }));

    await screen.findByText('Clara Souza');
    expect(api.router.state.location.search).toBe('?search=clara');
    expect(api.calls.some((call) => call.url.includes('search=clara'))).toBe(true);
  });

  it('filtra por situação com controle segmentado', async () => {
    const api = renderAdminArea('/admin/teachers', { [LIST]: { body: pageOf([TEACHER_ACTIVE]) } });
    const user = userEvent.setup();
    await screen.findByText('Clara Souza');
    expect(screen.getByRole('button', { name: 'Todos' })).toHaveAttribute('aria-pressed', 'true');

    await user.click(screen.getByRole('button', { name: 'Ativos' }));
    await screen.findByText('Clara Souza');
    expect(screen.getByRole('button', { name: 'Ativos' })).toHaveAttribute('aria-pressed', 'true');
    expect(api.calls.some((call) => call.url.includes('active=true'))).toBe(true);

    await user.click(screen.getByRole('button', { name: 'Inativos' }));
    await screen.findByText('Clara Souza');
    expect(api.calls.some((call) => call.url.includes('active=false'))).toBe(true);
  });

  it('sem professores mostra o estado vazio com a ação que resolve', async () => {
    renderAdminArea('/admin/teachers', { [LIST]: { body: pageOf([]) } });
    expect(await screen.findByText('Nenhum professor cadastrado ainda.')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Cadastrar professor' }).length).toBeGreaterThan(0);
  });

  it('busca sem resultado oferece limpar a busca', async () => {
    renderAdminArea('/admin/teachers?search=zzz', { [LIST]: { body: pageOf([]) } });
    expect(await screen.findByText('Nenhum professor encontrado para “zzz”.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Limpar busca' })).toBeInTheDocument();
  });

  it('erro mostra alerta com "Tentar novamente"', async () => {
    renderAdminArea('/admin/teachers', {
      [LIST]: { status: 500, body: { code: 'INTERNAL_ERROR', message: 'Ocorreu um erro inesperado.' } },
    });
    expect(await screen.findByRole('alert')).toHaveTextContent('Ocorreu um erro inesperado.');
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument();
  });

  it('carregando mostra os esqueletos anunciados', async () => {
    renderAdminArea('/admin/teachers', { [LIST]: () => new Promise(() => undefined) });
    expect(await screen.findByRole('status', { name: 'Carregando professores' })).toHaveAttribute(
      'aria-busy',
      'true',
    );
  });

  it('pagina com Anterior/Próxima e total', async () => {
    const api = renderAdminArea('/admin/teachers', {
      [LIST]: { body: pageOf([TEACHER_ACTIVE], { page: 0, size: 20, total: 45 }) },
    });
    const user = userEvent.setup();
    expect(await screen.findByText('Página 1 de 3 · 45 professor(es)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Próxima' }));
    await screen.findByText('Clara Souza');
    expect(api.calls.some((call) => call.url.includes('page=1'))).toBe(true);
  });
});
