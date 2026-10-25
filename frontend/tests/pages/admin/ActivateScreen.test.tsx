import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { adminRoutes } from '../../../src/app/adminRoutes';
import { mockApi, renderRoutes } from '../../utils';
import { blockedError, validationError } from '../../support/adminFixtures';

const ACTIVATE = 'POST /api/v1/admin/activation';
const VALID_PASSWORD = 'a-long-valid-password';

async function fillAll(
  user: ReturnType<typeof userEvent.setup>,
  options: { password?: string; confirmation?: string } = {},
) {
  await user.type(await screen.findByLabelText('E-mail'), 'renato@example.test');
  await user.type(screen.getByLabelText('Código de ativação'), 'k7qm2xpa9tbd');
  await user.type(screen.getByLabelText('Nova senha'), options.password ?? VALID_PASSWORD);
  await user.type(screen.getByLabelText('Confirme a nova senha'), options.confirmation ?? VALID_PASSWORD);
}

function open(apiRoutes: Parameters<typeof mockApi>[0] = {}) {
  const api = mockApi(apiRoutes);
  return { api, ...renderRoutes(adminRoutes, '/admin/activate') };
}

describe('ActivateScreen (A02)', () => {
  it('mostra os campos com os atributos corretos, a regra de tamanho visível e nenhum medidor de força', async () => {
    open();
    expect(await screen.findByRole('heading', { name: 'Definir a senha' })).toBeInTheDocument();
    expect(screen.getByLabelText('E-mail')).toHaveAttribute('autocomplete', 'username');
    const code = screen.getByLabelText('Código de ativação');
    expect(code).toHaveAttribute('autocomplete', 'off');
    expect(code).toHaveAttribute('autocapitalize', 'characters');
    expect(code).toHaveAttribute('type', 'text');
    for (const label of ['Nova senha', 'Confirme a nova senha']) {
      expect(screen.getByLabelText(label)).toHaveAttribute('autocomplete', 'new-password');
      expect(screen.getByLabelText(label)).toHaveAttribute('type', 'password');
    }
    expect(screen.getByText('A senha deve ter de 12 a 128 caracteres.')).toBeInTheDocument();
    expect(screen.queryByRole('meter')).not.toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('o código aparece em maiúsculas enquanto se digita', async () => {
    open();
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Código de ativação'), 'k7qm-2x');
    expect(screen.getByLabelText('Código de ativação')).toHaveValue('K7QM-2X');
  });

  it('define a senha: envia e-mail, código e nova senha (sem a confirmação) e oferece entrar', async () => {
    const { api, router } = open({ [ACTIVATE]: { status: 204 } });
    const user = userEvent.setup();
    await fillAll(user);
    await user.click(screen.getByRole('button', { name: 'Definir senha' }));

    expect(await screen.findByText('Senha definida. Agora você já pode entrar.')).toBeInTheDocument();
    const post = api.calls.find((call) => call.method === 'POST')!;
    expect(JSON.parse(post.init.body as string)).toEqual({
      email: 'renato@example.test',
      activationCode: 'K7QM2XPA9TBD',
      newPassword: VALID_PASSWORD,
    });
    expect(screen.getByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/admin/sign-in');
    expect(router.state.location.search).toBe('');
    expect(screen.queryByLabelText('Nova senha')).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain(VALID_PASSWORD);
  });

  it('confirmação diferente não chama o servidor', async () => {
    const { api } = open();
    const user = userEvent.setup();
    await fillAll(user, { confirmation: 'a-different-password' });
    await user.click(screen.getByRole('button', { name: 'Definir senha' }));
    expect(screen.getAllByText('As senhas não são iguais.').length).toBeGreaterThan(0);
    expect(screen.getByLabelText('Confirme a nova senha')).toHaveAttribute('aria-invalid', 'true');
    expect(api.calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('campos vazios indicam o que falta e não chamam o servidor', async () => {
    const { api } = open();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Definir senha' }));
    expect(screen.getAllByText('Informe o e-mail.').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Informe o código de ativação.').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Informe a nova senha.').length).toBeGreaterThan(0);
    expect(api.calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('código inválido mostra a mensagem única do catálogo e limpa as senhas', async () => {
    open({ [ACTIVATE]: { status: 401, body: { code: 'INVALID_ACTIVATION', message: 'Texto do servidor' } } });
    const user = userEvent.setup();
    await fillAll(user);
    await user.click(screen.getByRole('button', { name: 'Definir senha' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Código de ativação inválido ou vencido. Peça um novo a outro administrador.',
    );
    expect(screen.getByLabelText('Nova senha')).toHaveValue('');
    expect(screen.getByLabelText('Confirme a nova senha')).toHaveValue('');
    expect(screen.getByLabelText('Código de ativação')).toHaveValue('K7QM2XPA9TBD');
  });

  it('erro de senha por campo aparece ligado à nova senha', async () => {
    open({
      [ACTIVATE]: {
        status: 400,
        body: validationError([{ field: 'newPassword', code: 'PASSWORD_EQUALS_IDENTITY' }]),
      },
    });
    const user = userEvent.setup();
    await fillAll(user);
    await user.click(screen.getByRole('button', { name: 'Definir senha' }));
    const field = screen.getByLabelText('Nova senha');
    await waitFor(() => expect(field).toHaveAttribute('aria-invalid', 'true'));
    expect(document.getElementById(field.getAttribute('aria-describedby')!.split(' ')[0]!)).toHaveTextContent(
      'A senha não pode ser igual ao seu e-mail ou ao seu nome.',
    );
  });

  it('bloqueio mostra o tempo formatado em região aria-live e desabilita o envio', async () => {
    open({ [ACTIVATE]: { status: 429, body: { ...blockedError(125), code: 'TEMPORARILY_BLOCKED' } } });
    const user = userEvent.setup();
    await fillAll(user);
    await user.click(screen.getByRole('button', { name: 'Definir senha' }));
    const live = await screen.findByRole('status');
    await waitFor(() => expect(live).toHaveTextContent('Tente novamente em 3 minuto(s).'));
    expect(live).toHaveAttribute('aria-live', 'polite');
    expect(document.body.textContent).not.toContain('{minutes}');
    expect(screen.getByRole('button', { name: 'Definir senha' })).toBeDisabled();
  });

  it('oferece voltar à entrada', async () => {
    open();
    expect(await screen.findByRole('link', { name: 'Já tenho senha' })).toHaveAttribute(
      'href',
      '/admin/sign-in',
    );
  });
});
