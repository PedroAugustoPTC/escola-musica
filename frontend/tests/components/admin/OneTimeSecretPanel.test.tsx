import { describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { Link, type RouteObject } from 'react-router';
import { OneTimeSecretPanel } from '../../../src/components/admin/OneTimeSecretPanel';
import { renderRoutes } from '../../utils';
import { ADMINISTRATOR_ACTIVATION_CODE, TEACHER_ACCESS_CODE } from '../../support/adminFixtures';

function stubClipboard(writeText: (text: string) => Promise<void>) {
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
}

function renderPanel(overrides: Partial<Parameters<typeof OneTimeSecretPanel>[0]> = {}) {
  const onAcknowledge = vi.fn();
  const routes: RouteObject[] = [
    {
      path: '/admin/teachers/new',
      element: (
        <>
          <OneTimeSecretPanel
            kind="access-code"
            secret={TEACHER_ACCESS_CODE}
            recipientName="Clara Souza"
            validUntil="2099-09-28T12:00:00Z"
            onAcknowledge={onAcknowledge}
            {...overrides}
          />
          <Link to="/admin/teachers">Ir para a lista</Link>
        </>
      ),
    },
    { path: '/admin/teachers', element: <h1>Lista de professores</h1> },
  ];
  return { ...renderRoutes(routes, '/admin/teachers/new'), onAcknowledge };
}

describe('OneTimeSecretPanel', () => {
  it('mostra o código, para quem é, a validade e o aviso de que aparece uma só vez', () => {
    renderPanel();
    expect(screen.getByText(TEACHER_ACCESS_CODE)).toBeInTheDocument();
    expect(screen.getByText(/Código de acesso de Clara Souza/)).toBeInTheDocument();
    expect(screen.getByText(/Válido até 28\/09\/2099 às 09:00/)).toBeInTheDocument();
    expect(screen.getByText(/Este código aparece uma única vez/)).toBeInTheDocument();
  });

  it('o código de ativação tem o próprio rótulo', () => {
    renderPanel({
      kind: 'activation-code',
      secret: ADMINISTRATOR_ACTIVATION_CODE,
      recipientName: 'Renato Alves',
    });
    expect(screen.getByText(/Código de ativação de Renato Alves/)).toBeInTheDocument();
    expect(screen.getByText(ADMINISTRATOR_ACTIVATION_CODE)).toBeInTheDocument();
  });

  it('"Copiar" copia o código e anuncia em role="status"', async () => {
    const writeText = vi.fn(async () => undefined);
    const user = userEvent.setup();
    renderPanel();
    stubClipboard(writeText);
    await user.click(screen.getByRole('button', { name: 'Copiar' }));
    expect(writeText).toHaveBeenCalledWith(TEACHER_ACCESS_CODE);
    expect(await screen.findByRole('status')).toHaveTextContent('Código copiado.');
  });

  it('se a cópia falhar, orienta a copiar à mão', async () => {
    const user = userEvent.setup();
    renderPanel();
    stubClipboard(async () => {
      throw new Error('denied');
    });
    await user.click(screen.getByRole('button', { name: 'Copiar' }));
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Não foi possível copiar. Selecione o código e copie à mão.',
    );
  });

  it('o valor não vai para armazenamento, URL, título nem cache de consulta', async () => {
    const { client } = renderPanel();
    await act(async () => undefined);
    const everywhere = [
      JSON.stringify({ ...window.localStorage }),
      JSON.stringify({ ...window.sessionStorage }),
      window.location.href,
      document.title,
      JSON.stringify(
        client
          .getQueryCache()
          .getAll()
          .map((query) => query.state.data ?? null),
      ),
      JSON.stringify(
        client
          .getMutationCache()
          .getAll()
          .map((mutation) => mutation.state.data ?? null),
      ),
    ].join('|');
    expect(everywhere).not.toContain(TEACHER_ACCESS_CODE);
  });

  it('"Já anotei o código" apaga o valor e avisa quem chamou', async () => {
    const user = userEvent.setup();
    const { onAcknowledge } = renderPanel();
    await user.click(screen.getByRole('button', { name: 'Já anotei o código' }));
    expect(onAcknowledge).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(TEACHER_ACCESS_CODE)).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain(TEACHER_ACCESS_CODE);
  });

  it('enquanto o código está na tela, sair da página pede confirmação; ao anotar, a navegação é liberada', async () => {
    const user = userEvent.setup();
    const { router } = renderPanel();

    await user.click(screen.getByRole('link', { name: 'Ir para a lista' }));
    const dialog = await screen.findByRole('dialog', { name: 'Sair sem anotar o código?' });
    expect(router.state.location.pathname).toBe('/admin/teachers/new');

    await user.click(screen.getByRole('button', { name: 'Voltar e anotar' }));
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe('/admin/teachers/new');
    expect(screen.getByText(TEACHER_ACCESS_CODE)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Já anotei o código' }));
    await user.click(screen.getByRole('link', { name: 'Ir para a lista' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/teachers'));
  });

  it('sair mesmo assim segue a navegação', async () => {
    const user = userEvent.setup();
    const { router } = renderPanel();
    await user.click(screen.getByRole('link', { name: 'Ir para a lista' }));
    await user.click(await screen.findByRole('button', { name: 'Sair mesmo assim' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/teachers'));
  });

  it('um código novo recebido com o painel aberto substitui o exibido (nunca fica o antigo)', async () => {
    const newCode = 'N3WC0D3X';
    function Swappable() {
      const [secret, setSecret] = useState(TEACHER_ACCESS_CODE);
      return (
        <>
          <OneTimeSecretPanel
            kind="access-code"
            secret={secret}
            recipientName="Clara Souza"
            validUntil="2099-09-28T12:00:00Z"
            onAcknowledge={() => undefined}
          />
          <button type="button" onClick={() => setSecret(newCode)}>
            Emitir de novo
          </button>
        </>
      );
    }
    const user = userEvent.setup();
    renderRoutes([{ path: '*', element: <Swappable /> }], '/admin/teachers/new');
    expect(screen.getByText(TEACHER_ACCESS_CODE)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Emitir de novo' }));

    expect(screen.getByText(newCode)).toBeInTheDocument();
    expect(screen.queryByText(TEACHER_ACCESS_CODE)).not.toBeInTheDocument();
  });
});
