import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TEACHER_ACCESS_CODE, TEACHER_ACTIVE, TEACHER_INACTIVE } from '../../support/adminFixtures';
import { renderAdminArea } from './adminTestSupport';

const PATH = `/api/v1/admin/teachers/${TEACHER_ACTIVE.id}`;
const NEW_CODE = 'NEWC0D3X';
const REISSUED = {
  teacher: TEACHER_ACTIVE,
  accessCode: NEW_CODE,
  validUntil: '2099-09-28T12:00:00Z',
};

function open(teacher = TEACHER_ACTIVE, apiRoutes: Parameters<typeof renderAdminArea>[1] = {}) {
  return renderAdminArea(`/admin/teachers/${teacher.id}`, {
    [`GET /api/v1/admin/teachers/${teacher.id}`]: { body: teacher },
    ...apiRoutes,
  });
}

describe('TeacherDetailScreen — reemissão do código e desvinculação do Telegram', () => {
  it('a reemissão nomeia a ação e o professor e oferece a caixa para encerrar o vínculo do Telegram', async () => {
    open();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Reemitir código' }));

    const dialog = await screen.findByRole('dialog', { name: 'Reemitir código de acesso' });
    expect(dialog).toHaveTextContent(
      'Reemitir o código de Clara Souza? O código atual deixa de valer e as sessões da Web são encerradas.',
    );
    const box = within(dialog).getByRole('checkbox', { name: 'Também encerrar o vínculo do Telegram' });
    expect(box).not.toBeChecked();
  });

  it('o código novo vai ao painel de código único e a requisição leva a opção escolhida', async () => {
    const api = open(TEACHER_ACTIVE, {
      [`POST ${PATH}/access-code`]: { body: REISSUED },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Reemitir código' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('checkbox'));
    await user.click(within(dialog).getByRole('button', { name: 'Reemitir' }));

    expect(await screen.findByText(NEW_CODE)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    const post = api.calls.find((call) => call.method === 'POST')!;
    expect(JSON.parse(post.init.body as string)).toEqual({ endTelegramLink: true });
    expect(
      JSON.stringify(
        api.client
          .getQueryCache()
          .getAll()
          .map((q) => q.state.data ?? null),
      ),
    ).not.toContain(NEW_CODE);
    expect(
      JSON.stringify(
        api.client
          .getMutationCache()
          .getAll()
          .map((m) => m.state.data ?? null),
      ),
    ).not.toContain(NEW_CODE);

    await user.click(screen.getByRole('button', { name: 'Já anotei o código' }));
    expect(screen.queryByText(NEW_CODE)).not.toBeInTheDocument();
  });

  it('sem marcar a caixa o vínculo do Telegram é mantido', async () => {
    const api = open(TEACHER_ACTIVE, { [`POST ${PATH}/access-code`]: { body: REISSUED } });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Reemitir código' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Reemitir' }));
    await screen.findByText(NEW_CODE);
    const post = api.calls.find((call) => call.method === 'POST')!;
    expect(JSON.parse(post.init.body as string)).toEqual({ endTelegramLink: false });
  });

  it('cancelar (Esc) não grava e devolve o foco ao botão', async () => {
    const api = open();
    const user = userEvent.setup();
    const trigger = await screen.findByRole('button', { name: 'Reemitir código' });
    await user.click(trigger);
    await screen.findByRole('dialog');
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
    expect(api.calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('falha na reemissão mantém o diálogo com o erro e não mostra código', async () => {
    open(TEACHER_ACTIVE, {
      [`POST ${PATH}/access-code`]: { status: 500, body: { code: 'OPERATION_NOT_SAVED', message: 'x' } },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Reemitir código' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Reemitir' }));
    expect(await within(await screen.findByRole('dialog')).findByRole('alert')).toHaveTextContent(
      'Nada foi gravado',
    );
    expect(document.body.textContent).not.toContain(NEW_CODE);
  });

  it('a desvinculação pede confirmação, grava e passa a mostrar o Telegram como não vinculado', async () => {
    let linked = true;
    const api = open(TEACHER_ACTIVE, {
      [`GET ${PATH}`]: () => ({ body: { ...TEACHER_ACTIVE, telegramLinked: linked } }),
      [`DELETE ${PATH}/telegram-link`]: () => {
        linked = false;
        return { status: 204 };
      },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Desvincular Telegram' }));
    const dialog = await screen.findByRole('dialog', { name: 'Desvincular Telegram' });
    expect(dialog).toHaveTextContent(
      'Desvincular o Telegram de Clara Souza? Ele(a) precisará enviar um código de acesso válido para vincular de novo.',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Desvincular' }));

    expect(await screen.findByText('Vínculo do Telegram encerrado.')).toBeInTheDocument();
    expect(screen.getByText('Telegram não vinculado')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Desvincular Telegram' })).not.toBeInTheDocument();
    expect(api.calls.some((call) => call.method === 'DELETE')).toBe(true);
  });

  it('cancelar a desvinculação não grava', async () => {
    const api = open();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Desvincular Telegram' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Voltar' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(api.calls.some((call) => call.method === 'DELETE')).toBe(false);
  });

  it('sem Telegram vinculado não há desvinculação nem a caixa na reemissão', async () => {
    open({ ...TEACHER_ACTIVE, telegramLinked: false });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Reemitir código' }));
    expect(screen.queryByRole('button', { name: 'Desvincular Telegram' })).not.toBeInTheDocument();
    expect(within(await screen.findByRole('dialog')).queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('professor inativo mostra a orientação de reativar no lugar da reemissão', async () => {
    open(TEACHER_INACTIVE);
    expect(
      await screen.findByText('Este professor está inativo. Reative-o para emitir um código de acesso novo.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reemitir código' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reativar' })).toBeInTheDocument();
    expect(document.body.textContent).not.toContain(TEACHER_ACCESS_CODE);
  });

  it('professor inativado entre a abertura e a reemissão mostra o texto do catálogo', async () => {
    open(TEACHER_ACTIVE, {
      [`POST ${PATH}/access-code`]: { status: 409, body: { code: 'TEACHER_INACTIVE', message: 'x' } },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Reemitir código' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Reemitir' }));
    expect(await within(await screen.findByRole('dialog')).findByRole('alert')).toHaveTextContent(
      'Este professor está inativo. Reative-o antes.',
    );
  });
});
