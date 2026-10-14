import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DialogoConfirmacao } from '../../src/componentes/DialogoConfirmacao';
import { Botao } from '../../src/componentes/Botao';
import { EstadoVazio } from '../../src/componentes/EstadoVazio';
import { AlertaErro, textoDoErro } from '../../src/componentes/EstadoErro';
import { Cabecalho, iniciais } from '../../src/componentes/Cabecalho';
import { EsqueletoCartoes, EstadoCarregando } from '../../src/componentes/EstadoCarregando';
import { ErroApi } from '../../src/servicos/clienteApi';
import { SESSAO_CLARA, renderizar, simularApi } from '../utilitarios';

describe('DialogoConfirmacao', () => {
  it('prende o foco, fecha com Esc e expõe rótulos acessíveis', async () => {
    const aoFechar = vi.fn();
    const aoConfirmar = vi.fn();
    const usuario = userEvent.setup();
    renderizar(
      <DialogoConfirmacao
        aberto
        titulo="Aprovar a versão 2?"
        texto="O relatório entrará no histórico."
        icone="verified"
        rotuloConfirmar="Aprovar relatório"
        aoConfirmar={aoConfirmar}
        aoFechar={aoFechar}
      />,
    );

    const dialogo = screen.getByRole('dialog', { name: 'Aprovar a versão 2?' });
    expect(dialogo).toHaveAttribute('aria-modal', 'true');
    expect(dialogo).toHaveAccessibleDescription('O relatório entrará no histórico.');
    const confirmar = screen.getByRole('button', { name: 'Aprovar relatório' });
    const voltar = screen.getByRole('button', { name: 'Voltar' });
    expect(confirmar).toHaveFocus();

    await usuario.tab();
    expect(voltar).toHaveFocus();
    await usuario.tab();
    expect(confirmar).toHaveFocus();
    await usuario.tab({ shift: true });
    expect(voltar).toHaveFocus();

    await usuario.click(confirmar);
    expect(aoConfirmar).toHaveBeenCalledOnce();
    await usuario.keyboard('{Escape}');
    expect(aoFechar).toHaveBeenCalledOnce();
  });

  it('mostra erro e bloqueia a confirmação enquanto carrega', () => {
    renderizar(
      <DialogoConfirmacao
        aberto
        perigo
        titulo="Cancelar este relatório?"
        texto="Não poderá ser recuperado."
        icone="block"
        rotuloConfirmar="Cancelar relatório"
        carregando
        erro="Falhou."
        aoConfirmar={() => undefined}
        aoFechar={() => undefined}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Falhou.');
    expect(screen.getByRole('button', { name: 'Cancelar relatório' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Voltar' })).toBeDisabled();
  });

  it('fechado não renderiza nada', () => {
    renderizar(
      <DialogoConfirmacao
        aberto={false}
        titulo="x"
        texto="y"
        icone="z"
        rotuloConfirmar="ok"
        aoConfirmar={() => undefined}
        aoFechar={() => undefined}
      />,
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});

describe('Botao', () => {
  it('fica ocupado e desabilitado ao carregar', () => {
    renderizar(
      <Botao carregando icone="send">
        Enviar
      </Botao>,
    );
    const botao = screen.getByRole('button', { name: 'Enviar' });
    expect(botao).toBeDisabled();
    expect(botao).toHaveAttribute('aria-busy', 'true');
    expect(botao).toHaveClass('botao', 'botao--primario');
  });
});

describe('Estados', () => {
  it('estado vazio com monograma e ação', () => {
    renderizar(
      <EstadoVazio
        monograma
        texto="Este aluno ainda não tem relatórios aprovados."
        acao={<button>Registrar aula</button>}
      />,
    );
    expect(screen.getByRole('status')).toHaveTextContent('Este aluno ainda não tem relatórios aprovados.');
    expect(screen.getByRole('button', { name: 'Registrar aula' })).toBeInTheDocument();
  });

  it('erro mostra o texto do catálogo e nunca detalhe interno', async () => {
    const tentar = vi.fn();
    renderizar(<AlertaErro erro={new ErroApi(0, null)} aoTentarDeNovo={tentar} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Não foi possível entrar agora.');
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    expect(tentar).toHaveBeenCalled();
    expect(textoDoErro(new Error('stack interno'))).toBe(
      'Ocorreu um erro inesperado. Tente novamente mais tarde.',
    );
  });

  it('carregando anuncia ocupado', () => {
    renderizar(
      <>
        <EstadoCarregando rotulo="Carregando histórico" />
        <EsqueletoCartoes rotulo="Carregando alunos" />
      </>,
    );
    expect(screen.getByLabelText('Carregando histórico')).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByLabelText('Carregando alunos')).toHaveAttribute('aria-busy', 'true');
  });
});

describe('Cabecalho', () => {
  it('menu alterna o tema e encerra a sessão só após 2xx', async () => {
    const api = simularApi({
      'GET /api/v1/session': { corpo: SESSAO_CLARA },
      'DELETE /api/v1/session': { status: 204 },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizar(<Cabecalho />);

    await usuario.click(screen.getByRole('button', { name: 'Menu' }));
    await waitFor(() => expect(screen.getByRole('menu')).toHaveTextContent('Clara Souza'));
    const tema = screen.getByRole('menuitemcheckbox', { name: /Modo escuro/ });
    expect(tema).toHaveAttribute('aria-checked', 'false');
    await usuario.click(tema);
    expect(document.documentElement.dataset.tema).toBe('escuro');
    expect(tema).toHaveAttribute('aria-checked', 'true');

    await usuario.click(screen.getByRole('menuitem', { name: 'Encerrar sessão' }));
    await waitFor(() => expect(roteador.state.location.pathname).toBe('/entrar'));
    expect(api.chamadas.some((c) => c.metodo === 'DELETE')).toBe(true);
    await usuario.click(document.body);
  });

  it('erro ao encerrar mantém a tela e mostra a mensagem', async () => {
    simularApi({
      'GET /api/v1/session': { corpo: SESSAO_CLARA },
      'DELETE /api/v1/session': {
        status: 500,
        corpo: {
          code: 'INTERNAL_ERROR',
          message: 'Ocorreu um erro inesperado. Tente novamente mais tarde.',
        },
      },
    });
    const usuario = userEvent.setup();
    const { roteador } = renderizar(<Cabecalho />, '/alunos');
    await usuario.click(screen.getByRole('button', { name: 'Menu' }));
    await usuario.click(screen.getByRole('menuitem', { name: 'Encerrar sessão' }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Ocorreu um erro inesperado'));
    expect(roteador.state.location.pathname).toBe('/alunos');
  });

  it('contexto móvel mostra voltar e o aluno', async () => {
    simularApi({ 'GET /api/v1/session': { corpo: SESSAO_CLARA } });
    const usuario = userEvent.setup();
    const { roteador } = renderizar(
      <Cabecalho
        contexto={{ sobretitulo: 'Novo relatório', titulo: 'Matteo Mafra', voltarPara: '/alunos' }}
      />,
      '/x',
    );
    expect(screen.getByText('Matteo Mafra')).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Voltar' }));
    expect(roteador.state.location.pathname).toBe('/alunos');
    await usuario.click(screen.getByRole('button', { name: 'Buscar aluno' }));
    expect(roteador.state.location.search).toBe('?foco=busca');
  });

  it('computador mostra navegação e busca', async () => {
    (globalThis as { __larguraTeste?: number }).__larguraTeste = 1440;
    simularApi({ 'GET /api/v1/session': { corpo: SESSAO_CLARA } });
    const usuario = userEvent.setup();
    const { roteador } = renderizar(<Cabecalho navAtiva="alunos" />);
    expect(screen.getByRole('button', { name: 'Alunos' })).toHaveAttribute('aria-current', 'page');
    await usuario.type(screen.getByRole('searchbox', { name: 'Buscar aluno' }), 'olavo{Enter}');
    expect(roteador.state.location.search).toBe('?busca=olavo');
    await waitFor(() => expect(screen.getByText('CS')).toBeInTheDocument());
    expect(iniciais('Clara de Souza Lima')).toBe('CL');
    expect(iniciais('Miguel de Castro Torrente')).toBe('MT');
    expect(iniciais('Matteo')).toBe('M');
  });
});
