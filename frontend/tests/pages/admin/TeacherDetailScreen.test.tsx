import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  TEACHER_ACCESS_CODE,
  TEACHER_ACTIVE,
  TEACHER_INACTIVE,
  TEACHER_WITH_CODE,
  validationError,
} from '../../support/adminFixtures';
import { renderAdminArea } from './adminTestSupport';

const PATH = `/api/v1/admin/teachers/${TEACHER_ACTIVE.id}`;
const INACTIVE_PATH = `/api/v1/admin/teachers/${TEACHER_INACTIVE.id}`;

describe('TeacherDetailScreen (A08)', () => {
  it('mostra os dados do professor, a situação com ícone e texto e o Telegram', async () => {
    renderAdminArea(`/admin/teachers/${TEACHER_ACTIVE.id}`, { [`GET ${PATH}`]: { body: TEACHER_ACTIVE } });
    expect(await screen.findByRole('heading', { name: 'Clara Souza' })).toBeInTheDocument();
    expect(screen.getByText('Ativo')).toBeInTheDocument();
    expect(screen.getByText('Telegram vinculado')).toBeInTheDocument();
    expect(screen.getByLabelText('Nome')).toHaveValue('Clara Souza');
    expect(screen.getByLabelText('E-mail')).toHaveValue('clara@example.test');
  });

  it('edita nome e e-mail e confirma em role="status"', async () => {
    const api = renderAdminArea(`/admin/teachers/${TEACHER_ACTIVE.id}`, {
      [`GET ${PATH}`]: { body: TEACHER_ACTIVE },
      [`PATCH ${PATH}`]: { body: { ...TEACHER_ACTIVE, name: 'Clara S. Souza' } },
    });
    const user = userEvent.setup();
    const name = await screen.findByLabelText('Nome');
    await user.clear(name);
    await user.type(name, 'Clara S. Souza');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Dados salvos.')).toBeInTheDocument();
    const patch = api.calls.find((call) => call.method === 'PATCH')!;
    expect(JSON.parse(patch.init.body as string)).toEqual({
      name: 'Clara S. Souza',
      email: 'clara@example.test',
    });
    expect(await screen.findByRole('heading', { name: 'Clara S. Souza' })).toBeInTheDocument();
  });

  it('e-mail repetido na edição mostra o nome do professor existente', async () => {
    renderAdminArea(`/admin/teachers/${TEACHER_ACTIVE.id}`, {
      [`GET ${PATH}`]: { body: TEACHER_ACTIVE },
      [`PATCH ${PATH}`]: {
        status: 409,
        body: { code: 'DUPLICATE_EMAIL', message: 'x', details: { name: 'Bruno Lima' } },
      },
    });
    const user = userEvent.setup();
    await screen.findByLabelText('Nome');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Já existe um cadastro com este e-mail: Bruno Lima.',
    );
  });

  it('erros por campo da edição aparecem no resumo', async () => {
    renderAdminArea(`/admin/teachers/${TEACHER_ACTIVE.id}`, {
      [`GET ${PATH}`]: { body: TEACHER_ACTIVE },
      [`PATCH ${PATH}`]: { status: 400, body: validationError([{ field: 'email', code: 'INVALID_EMAIL' }]) },
    });
    const user = userEvent.setup();
    await screen.findByLabelText('Nome');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Informe um e-mail válido');
  });

  it('inativar pede confirmação nomeando a ação e o alvo; Esc cancela e o foco volta', async () => {
    const api = renderAdminArea(`/admin/teachers/${TEACHER_ACTIVE.id}`, {
      [`GET ${PATH}`]: { body: TEACHER_ACTIVE },
    });
    const user = userEvent.setup();
    const trigger = await screen.findByRole('button', { name: 'Inativar' });
    await user.click(trigger);

    const dialog = await screen.findByRole('dialog', { name: 'Inativar professor' });
    expect(dialog).toHaveTextContent(
      'Inativar Clara Souza? O acesso dele(a) termina agora, na Web e no Telegram. Relatórios em aberto ficam preservados.',
    );
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
    expect(api.calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('confirmar a inativação grava e passa a oferecer "Reativar"', async () => {
    let active = true;
    renderAdminArea(`/admin/teachers/${TEACHER_ACTIVE.id}`, {
      [`GET ${PATH}`]: () => ({ body: { ...TEACHER_ACTIVE, active } }),
      [`POST ${PATH}/inactivation`]: () => {
        active = false;
        return { body: { ...TEACHER_ACTIVE, active: false, telegramLinked: false } };
      },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Inativar' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Inativar' }));

    expect(await screen.findByRole('button', { name: 'Reativar' })).toBeInTheDocument();
    expect(screen.getByText('Inativo')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('falha ao inativar mantém o diálogo com o erro e não muda a tela', async () => {
    renderAdminArea(`/admin/teachers/${TEACHER_ACTIVE.id}`, {
      [`GET ${PATH}`]: { body: TEACHER_ACTIVE },
      [`POST ${PATH}/inactivation`]: { status: 500, body: { code: 'OPERATION_NOT_SAVED', message: 'x' } },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Inativar' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Inativar' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Nada foi gravado');
    expect(screen.getByText('Ativo')).toBeInTheDocument();
  });

  it('reativar mostra o código novo uma única vez', async () => {
    const api = renderAdminArea(`/admin/teachers/${TEACHER_INACTIVE.id}`, {
      [`GET ${INACTIVE_PATH}`]: { body: TEACHER_INACTIVE },
      [`POST ${INACTIVE_PATH}/reactivation`]: {
        body: { ...TEACHER_WITH_CODE, teacher: { ...TEACHER_INACTIVE, active: true } },
      },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Reativar' }));

    expect(await screen.findByText(TEACHER_ACCESS_CODE)).toBeInTheDocument();
    expect(
      JSON.stringify(
        api.client
          .getQueryCache()
          .getAll()
          .map((q) => q.state.data ?? null),
      ),
    ).not.toContain(TEACHER_ACCESS_CODE);
    await user.click(screen.getByRole('button', { name: 'Já anotei o código' }));
    expect(screen.queryByText(TEACHER_ACCESS_CODE)).not.toBeInTheDocument();
    expect(await screen.findByText('Ativo')).toBeInTheDocument();
  });

  it('professor inexistente mostra o erro do servidor', async () => {
    renderAdminArea('/admin/teachers/0190a000-0000-7000-8000-00000000ffff', {
      'GET /api/v1/admin/teachers/0190a000-0000-7000-8000-00000000ffff': {
        status: 404,
        body: { code: 'RESOURCE_NOT_FOUND', message: 'Não encontramos o que você procurou.' },
      },
    });
    expect(await screen.findByRole('alert')).toHaveTextContent('Não encontramos o que você procurou.');
  });
});
