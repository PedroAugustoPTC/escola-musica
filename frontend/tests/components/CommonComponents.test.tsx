import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfirmationDialog } from '../../src/components/ConfirmationDialog';
import { Button } from '../../src/components/Button';
import { EmptyState } from '../../src/components/EmptyState';
import { ErrorAlert, errorText } from '../../src/components/ErrorState';
import { Header, initials } from '../../src/components/Header';
import { CardsSkeleton, LoadingState } from '../../src/components/LoadingState';
import { ApiError } from '../../src/services/apiClient';
import { LIGHT_SESSION, renderWithProviders, mockApi } from '../utils';

describe('ConfirmationDialog', () => {
  it('prende o foco, fecha com Esc e expõe rótulos acessíveis', async () => {
    const onClose = vi.fn();
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ConfirmationDialog
        open
        title="Aprovar a versão 2?"
        text="O relatório entrará no histórico."
        icon="verified"
        confirmLabel="Aprovar relatório"
        onConfirm={onConfirm}
        onClose={onClose}
      />,
    );

    const dialog = screen.getByRole('dialog', { name: 'Aprovar a versão 2?' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleDescription('O relatório entrará no histórico.');
    const confirm = screen.getByRole('button', { name: 'Aprovar relatório' });
    const back = screen.getByRole('button', { name: 'Voltar' });
    expect(confirm).toHaveFocus();

    await user.tab();
    expect(back).toHaveFocus();
    await user.tab();
    expect(confirm).toHaveFocus();
    await user.tab({ shift: true });
    expect(back).toHaveFocus();

    await user.click(confirm);
    expect(onConfirm).toHaveBeenCalledOnce();
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('mostra erro e bloqueia a confirmação enquanto carrega', () => {
    renderWithProviders(
      <ConfirmationDialog
        open
        danger
        title="Cancelar este relatório?"
        text="Não poderá ser recuperado."
        icon="block"
        confirmLabel="Cancelar relatório"
        loading
        error="Falhou."
        onConfirm={() => undefined}
        onClose={() => undefined}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Falhou.');
    expect(screen.getByRole('button', { name: 'Cancelar relatório' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Voltar' })).toBeDisabled();
  });

  it('fechado não renderiza nada', () => {
    renderWithProviders(
      <ConfirmationDialog
        open={false}
        title="x"
        text="y"
        icon="z"
        confirmLabel="ok"
        onConfirm={() => undefined}
        onClose={() => undefined}
      />,
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});

describe('Botao', () => {
  it('fica ocupado e desabilitado ao carregar', () => {
    renderWithProviders(
      <Button loading icon="send">
        Enviar
      </Button>,
    );
    const button = screen.getByRole('button', { name: 'Enviar' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    expect(button).toHaveClass('button', 'button--primary');
  });
});

describe('Estados', () => {
  it('estado vazio com monograma e ação', () => {
    renderWithProviders(
      <EmptyState
        monogram
        text="Este aluno ainda não tem relatórios aprovados."
        action={<button>Registrar aula</button>}
      />,
    );
    expect(screen.getByRole('status')).toHaveTextContent('Este aluno ainda não tem relatórios aprovados.');
    expect(screen.getByRole('button', { name: 'Registrar aula' })).toBeInTheDocument();
  });

  it('erro mostra o texto do catálogo e nunca detalhe interno', async () => {
    const retry = vi.fn();
    renderWithProviders(<ErrorAlert error={new ApiError(0, null)} onRetry={retry} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Não foi possível entrar agora.');
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    expect(retry).toHaveBeenCalled();
    expect(errorText(new Error('stack interno'))).toBe(
      'Ocorreu um erro inesperado. Tente novamente mais tarde.',
    );
  });

  it('carregando anuncia ocupado', () => {
    renderWithProviders(
      <>
        <LoadingState label="Carregando histórico" />
        <CardsSkeleton label="Carregando alunos" />
      </>,
    );
    expect(screen.getByLabelText('Carregando histórico')).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByLabelText('Carregando alunos')).toHaveAttribute('aria-busy', 'true');
  });
});

describe('Cabecalho', () => {
  it('menu alterna o tema e encerra a sessão só após 2xx', async () => {
    const api = mockApi({
      'GET /api/v1/session': { body: LIGHT_SESSION },
      'DELETE /api/v1/session': { status: 204 },
    });
    const user = userEvent.setup();
    const { router } = renderWithProviders(<Header />);

    await user.click(screen.getByRole('button', { name: 'Menu' }));
    await waitFor(() => expect(screen.getByRole('menu')).toHaveTextContent('Clara Souza'));
    const theme = screen.getByRole('menuitemcheckbox', { name: /Modo escuro/ });
    expect(theme).toHaveAttribute('aria-checked', 'false');
    await user.click(theme);
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(theme).toHaveAttribute('aria-checked', 'true');

    await user.click(screen.getByRole('menuitem', { name: 'Encerrar sessão' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/sign-in'));
    expect(api.calls.some((c) => c.method === 'DELETE')).toBe(true);
    await user.click(document.body);
  });

  it('erro ao encerrar mantém a tela e mostra a mensagem', async () => {
    mockApi({
      'GET /api/v1/session': { body: LIGHT_SESSION },
      'DELETE /api/v1/session': {
        status: 500,
        body: {
          code: 'INTERNAL_ERROR',
          message: 'Ocorreu um erro inesperado. Tente novamente mais tarde.',
        },
      },
    });
    const user = userEvent.setup();
    const { router } = renderWithProviders(<Header />, '/students');
    await user.click(screen.getByRole('button', { name: 'Menu' }));
    await user.click(screen.getByRole('menuitem', { name: 'Encerrar sessão' }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Ocorreu um erro inesperado'));
    expect(router.state.location.pathname).toBe('/students');
  });

  it('contexto móvel mostra voltar e o aluno', async () => {
    mockApi({ 'GET /api/v1/session': { body: LIGHT_SESSION } });
    const user = userEvent.setup();
    const { router } = renderWithProviders(
      <Header context={{ kicker: 'Novo relatório', title: 'Matteo Mafra', backTo: '/students' }} />,
      '/x',
    );
    expect(screen.getByText('Matteo Mafra')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Voltar' }));
    expect(router.state.location.pathname).toBe('/students');
    await user.click(screen.getByRole('button', { name: 'Buscar aluno' }));
    expect(router.state.location.search).toBe('?focus=search');
  });

  it('computador mostra navegação e busca', async () => {
    (globalThis as { __testWidth?: number }).__testWidth = 1440;
    mockApi({ 'GET /api/v1/session': { body: LIGHT_SESSION } });
    const user = userEvent.setup();
    const { router } = renderWithProviders(<Header activeNav="students" />);
    expect(screen.getByRole('button', { name: 'Alunos' })).toHaveAttribute('aria-current', 'page');
    await user.type(screen.getByRole('searchbox', { name: 'Buscar aluno' }), 'olavo{Enter}');
    expect(router.state.location.search).toBe('?search=olavo');
    await waitFor(() => expect(screen.getByText('CS')).toBeInTheDocument());
    expect(initials('Clara de Souza Lima')).toBe('CL');
    expect(initials('Miguel de Castro Torrente')).toBe('MT');
    expect(initials('Matteo')).toBe('M');
  });
});
