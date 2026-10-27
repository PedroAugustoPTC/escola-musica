import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  ADMINISTRATOR_ACTIVATION_CODE,
  ADMINISTRATOR_ACTIVE,
  ADMINISTRATOR_PENDING,
  ADMINISTRATOR_WITH_ACTIVATION_CODE,
  pageOf,
} from '../../support/adminFixtures';
import { renderAdminArea } from './adminTestSupport';

const LIST = 'GET /api/v1/admin/administrators';
const INACTIVE = { ...ADMINISTRATOR_PENDING, active: false };

function open(apiRoutes: Parameters<typeof renderAdminArea>[1] = {}) {
  return renderAdminArea('/admin/administrators', {
    [LIST]: { body: pageOf([ADMINISTRATOR_ACTIVE, ADMINISTRATOR_PENDING]) },
    ...apiRoutes,
  });
}

const row = (name: string) => screen.getAllByRole('listitem').find((item) => within(item).queryByText(name))!;

describe('AdministratorsScreen (A09)', () => {
  it('lista com "Ativação pendente" ou "Ativado em", situação em ícone e texto', async () => {
    open();
    expect(await screen.findByRole('heading', { name: 'Administradores' })).toBeInTheDocument();
    await screen.findByText('marina@example.test');
    const activated = row('Marina Duarte');
    expect(within(activated).getByText('marina@example.test')).toBeInTheDocument();
    expect(within(activated).getByText('Ativo')).toBeInTheDocument();
    expect(within(activated).getByText('Ativado em 01/09/2026')).toBeInTheDocument();
    const pending = row('Renato Alves');
    expect(within(pending).getByText('Ativação pendente')).toBeInTheDocument();
    expect(document.body.textContent).not.toContain(ADMINISTRATOR_ACTIVATION_CODE);
  });

  it('filtra por situação e pagina', async () => {
    const api = open({ [LIST]: { body: pageOf([ADMINISTRATOR_ACTIVE], { total: 45 }) } });
    const user = userEvent.setup();
    expect(await screen.findByText('Página 1 de 3 · 45 administrador(es)')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ativos' }));
    await screen.findByText('marina@example.test');
    expect(api.calls.some((call) => call.url.includes('active=true'))).toBe(true);
    await user.click(screen.getByRole('button', { name: 'Próxima' }));
    await waitFor(() => expect(api.calls.some((call) => call.url.includes('page=1'))).toBe(true));
  });

  it('erro de carga mostra alerta com nova tentativa', async () => {
    open({ [LIST]: { status: 500, body: { code: 'INTERNAL_ERROR', message: 'Falhou.' } } });
    expect(await screen.findByRole('alert')).toHaveTextContent('Falhou.');
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument();
  });

  it('criar mostra o código de ativação uma única vez no painel e depois o apaga', async () => {
    const api = open({
      'POST /api/v1/admin/administrators': { status: 201, body: ADMINISTRATOR_WITH_ACTIVATION_CODE },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Criar administrador' }));
    await user.type(screen.getByLabelText('Nome'), 'Renato Alves');
    await user.type(screen.getByLabelText('E-mail'), 'renato@example.test');
    await user.click(screen.getByRole('button', { name: 'Criar' }));

    expect(await screen.findByText(ADMINISTRATOR_ACTIVATION_CODE)).toBeInTheDocument();
    expect(screen.getByText(/Código de ativação de Renato Alves/)).toBeInTheDocument();
    const post = api.calls.find((call) => call.method === 'POST')!;
    expect(JSON.parse(post.init.body as string)).toEqual({
      name: 'Renato Alves',
      email: 'renato@example.test',
    });
    expect(
      JSON.stringify(
        api.client
          .getQueryCache()
          .getAll()
          .map((q) => q.state.data ?? null),
      ),
    ).not.toContain(ADMINISTRATOR_ACTIVATION_CODE);

    await user.click(screen.getByRole('button', { name: 'Já anotei o código' }));
    expect(screen.queryByText(ADMINISTRATOR_ACTIVATION_CODE)).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Nome')).not.toBeInTheDocument();
  });

  it('e-mail repetido na criação mostra o nome do cadastro existente', async () => {
    open({
      'POST /api/v1/admin/administrators': {
        status: 409,
        body: { code: 'DUPLICATE_EMAIL', message: 'x', details: { name: 'Marina Duarte' } },
      },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Criar administrador' }));
    await user.type(screen.getByLabelText('Nome'), 'Outra Marina');
    await user.type(screen.getByLabelText('E-mail'), 'marina@example.test');
    await user.click(screen.getByRole('button', { name: 'Criar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Já existe um cadastro com este e-mail: Marina Duarte.',
    );
  });

  it('inativar pede confirmação nomeando a ação e o alvo; Esc cancela e nada é gravado', async () => {
    const api = open();
    const user = userEvent.setup();
    await screen.findByText('Renato Alves');
    const trigger = within(row('Renato Alves')).getByRole('button', { name: 'Inativar Renato Alves' });
    await user.click(trigger);

    const dialog = await screen.findByRole('dialog', { name: 'Inativar administrador' });
    expect(dialog).toHaveTextContent('Inativar Renato Alves? As sessões dele(a) terminam agora.');
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
    expect(api.calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('confirmar inativa e a lista passa a mostrar "Inativo" com "Reativar"', async () => {
    let inactive = false;
    open({
      [LIST]: () => ({ body: pageOf([ADMINISTRATOR_ACTIVE, inactive ? INACTIVE : ADMINISTRATOR_PENDING]) }),
      [`POST /api/v1/admin/administrators/${ADMINISTRATOR_PENDING.id}/inactivation`]: () => {
        inactive = true;
        return { body: INACTIVE };
      },
    });
    const user = userEvent.setup();
    await screen.findByText('Renato Alves');
    await user.click(within(row('Renato Alves')).getByRole('button', { name: 'Inativar Renato Alves' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Inativar' }));

    await waitFor(() => expect(within(row('Renato Alves')).getByText('Inativo')).toBeInTheDocument());
    expect(
      within(row('Renato Alves')).getByRole('button', { name: 'Reativar Renato Alves' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('o último administrador ativo não pode ser inativado: mostra LAST_ADMINISTRATOR no diálogo', async () => {
    open({
      [`POST /api/v1/admin/administrators/${ADMINISTRATOR_ACTIVE.id}/inactivation`]: {
        status: 409,
        body: { code: 'LAST_ADMINISTRATOR', message: 'x' },
      },
    });
    const user = userEvent.setup();
    await screen.findByRole('button', { name: 'Inativar Marina Duarte' });
    await user.click(within(row('Marina Duarte')).getByRole('button', { name: 'Inativar Marina Duarte' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Inativar' }));
    expect(await within(await screen.findByRole('dialog')).findByRole('alert')).toHaveTextContent(
      'A escola precisa ter pelo menos um administrador ativo.',
    );
    expect(within(row('Marina Duarte')).getByText('Ativo')).toBeInTheDocument();
  });

  it('reativar mostra o novo código de ativação uma única vez', async () => {
    const api = open({
      [LIST]: { body: pageOf([ADMINISTRATOR_ACTIVE, INACTIVE]) },
      [`POST /api/v1/admin/administrators/${INACTIVE.id}/reactivation`]: {
        body: ADMINISTRATOR_WITH_ACTIVATION_CODE,
      },
    });
    const user = userEvent.setup();
    await screen.findByText('Renato Alves');
    await user.click(within(row('Renato Alves')).getByRole('button', { name: 'Reativar Renato Alves' }));
    expect(await screen.findByText(ADMINISTRATOR_ACTIVATION_CODE)).toBeInTheDocument();
    expect(
      JSON.stringify(
        api.client
          .getMutationCache()
          .getAll()
          .map((m) => m.state.data ?? null),
      ),
    ).not.toContain(ADMINISTRATOR_ACTIVATION_CODE);
    await user.click(screen.getByRole('button', { name: 'Já anotei o código' }));
    expect(screen.queryByText(ADMINISTRATOR_ACTIVATION_CODE)).not.toBeInTheDocument();
  });

  it('com o código na tela, "Reativar" fica desabilitado em todas as linhas até o "Já anotei"', async () => {
    const other = {
      ...INACTIVE,
      id: '0190a000-0000-7000-8000-0000000000c9',
      name: 'Paula Reis',
      email: 'paula@example.test',
    };
    let reactivations = 0;
    open({
      [LIST]: { body: pageOf([INACTIVE, other]) },
      [`POST /api/v1/admin/administrators/${INACTIVE.id}/reactivation`]: () => {
        reactivations += 1;
        return { body: ADMINISTRATOR_WITH_ACTIVATION_CODE };
      },
    });
    const user = userEvent.setup();
    await screen.findByText('Paula Reis');
    await user.click(within(row('Renato Alves')).getByRole('button', { name: 'Reativar Renato Alves' }));
    expect(await screen.findByText(ADMINISTRATOR_ACTIVATION_CODE)).toBeInTheDocument();

    const buttons = screen.getAllByRole('button', { name: /^Reativar / });
    expect(buttons).toHaveLength(2);
    buttons.forEach((button) => expect(button).toBeDisabled());
    expect(reactivations).toBe(1);

    await user.click(screen.getByRole('button', { name: 'Já anotei o código' }));
    screen.getAllByRole('button', { name: /^Reativar / }).forEach((button) => expect(button).toBeEnabled());
  });
});
