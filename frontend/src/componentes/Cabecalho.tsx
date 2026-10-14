import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { useTema } from '../app/TemaProvedor';
import { useEhMovel } from '../app/useEhMovel';
import { useEncerrarSessao, useSessao } from '../servicos/sessao';
import { msg } from '../mensagens/mensagens';
import { Icone } from './Icone';
import { textoDoErro } from './EstadoErro';

export type ItemNavegacao = 'painel' | 'alunos' | 'registrar';

export interface ContextoCabecalho {
  sobretitulo: string;
  titulo: string;
  voltarPara: string;
}

export const ITENS_NAVEGACAO: { chave: ItemNavegacao; rotulo: string; icone: string; rota: string }[] = [
  { chave: 'painel', rotulo: 'Painel', icone: 'space_dashboard', rota: '/' },
  { chave: 'alunos', rotulo: 'Alunos', icone: 'group', rota: '/alunos' },
  { chave: 'registrar', rotulo: msg('REGISTER_LESSON_BUTTON'), icone: 'mic', rota: '/alunos?registrar=1' },
];

/** Iniciais do primeiro e do último nome ("Miguel de Castro Torrente" → "MT"). */
export function iniciais(nome: string): string {
  const partes = nome.split(/\s+/).filter(Boolean);
  const primeira = partes[0]?.[0] ?? '';
  const ultima = partes.length > 1 ? partes[partes.length - 1]![0]! : '';
  return (primeira + ultima).toUpperCase();
}

/** Menu do professor (02c): tema claro/escuro e "Encerrar sessão" (só navega após 2xx). */
function MenuProfessor({ aoFechar }: { aoFechar: () => void }) {
  const { tema, alternar } = useTema();
  const sessao = useSessao();
  const encerrar = useEncerrarSessao();
  const navegar = useNavigate();
  const nome = sessao.data?.teacher.name ?? '';
  const escuro = tema === 'escuro';

  return (
    <>
      <div className="menu-fundo" onClick={aoFechar} aria-hidden="true" />
      <div role="menu" aria-label="Menu do professor" className="menu-professor">
        <div className="menu-professor__identidade">
          <div style={{ fontWeight: 600 }}>{nome}</div>
          <div className="texto-apoio">Portal do Professor</div>
        </div>
        <button
          type="button"
          role="menuitemcheckbox"
          aria-checked={escuro}
          className="menu-professor__item"
          onClick={alternar}
        >
          <Icone nome="dark_mode" />
          <span style={{ flex: 1 }}>Modo escuro</span>
          <span className={`interruptor${escuro ? ' interruptor--ligado' : ''}`} aria-hidden="true">
            <span className="interruptor__botao" />
          </span>
        </button>
        <button
          type="button"
          role="menuitem"
          className="menu-professor__item"
          disabled={encerrar.isPending}
          onClick={() =>
            encerrar.mutate(undefined, {
              onSuccess: () => navegar('/entrar', { replace: true }),
            })
          }
        >
          <Icone nome="logout" />
          <span>{msg('END_SESSION_BUTTON')}</span>
        </button>
        {encerrar.isError ? (
          <div role="alert" className="campo__erro" style={{ padding: '6px 12px' }}>
            {textoDoErro(encerrar.error)}
          </div>
        ) : null}
      </div>
    </>
  );
}

/** Cabeçalho do portal nas versões móvel (60 px) e computador (76 px). */
export function Cabecalho({
  contexto,
  navAtiva,
}: {
  contexto?: ContextoCabecalho;
  navAtiva?: ItemNavegacao;
}) {
  const movel = useEhMovel();
  const { tinta } = useTema();
  const navegar = useNavigate();
  const sessao = useSessao();
  const [menuAberto, setMenuAberto] = useState(false);
  const [busca, setBusca] = useState('');
  const nome = sessao.data?.teacher.name ?? '';

  const botaoMenu = (
    <button
      type="button"
      className="botao botao--icone"
      aria-label="Menu"
      aria-expanded={menuAberto}
      aria-haspopup="menu"
      onClick={() => setMenuAberto((aberto) => !aberto)}
    >
      <Icone nome="menu" />
    </button>
  );

  if (movel) {
    return (
      <header className="cabecalho cabecalho--movel">
        {contexto ? (
          <>
            <button
              type="button"
              className="botao botao--icone"
              aria-label="Voltar"
              onClick={() => navegar(contexto.voltarPara)}
            >
              <Icone nome="arrow_back" />
            </button>
            <div className="cabecalho__contexto">
              <div className="cabecalho__contexto-sobretitulo">{contexto.sobretitulo}</div>
              <div className="cabecalho__contexto-titulo">{contexto.titulo}</div>
            </div>
          </>
        ) : (
          <button
            type="button"
            className="cabecalho__marca cabecalho__marca--movel"
            aria-label="Instituto Musical Felipe Blaft — Painel"
            onClick={() => navegar('/')}
          >
            <img
              src={`/marca/fb-monograma-${tinta}.png`}
              alt=""
              width={30}
              height={30}
              style={{ objectFit: 'contain' }}
            />
            <img
              src={`/marca/fb-wordmark-${tinta}.png`}
              alt=""
              width={112}
              height={25}
              style={{ objectFit: 'contain' }}
            />
          </button>
        )}
        <button
          type="button"
          className="botao botao--icone"
          aria-label="Buscar aluno"
          onClick={() => navegar('/alunos?foco=busca')}
        >
          <Icone nome="search" />
        </button>
        {botaoMenu}
        {menuAberto ? <MenuProfessor aoFechar={() => setMenuAberto(false)} /> : null}
      </header>
    );
  }

  const buscar = (evento: FormEvent) => {
    evento.preventDefault();
    const termo = busca.trim();
    navegar(termo ? `/alunos?busca=${encodeURIComponent(termo)}` : '/alunos');
  };

  return (
    <header className="cabecalho cabecalho--computador">
      <button
        type="button"
        className="cabecalho__marca"
        aria-label="Instituto Musical Felipe Blaft — Painel"
        onClick={() => navegar('/')}
      >
        <img
          src={`/marca/fb-monograma-${tinta}.png`}
          alt=""
          width={38}
          height={38}
          style={{ objectFit: 'contain' }}
        />
        <span className="cabecalho__filete" />
        <img
          src={`/marca/fb-wordmark-${tinta}.png`}
          alt=""
          width={150}
          height={33}
          style={{ objectFit: 'contain' }}
        />
      </button>
      <nav aria-label="Principal" className="cabecalho__nav">
        {ITENS_NAVEGACAO.map((item) => (
          <button
            key={item.chave}
            type="button"
            className="cabecalho__nav-item"
            aria-current={navAtiva === item.chave ? 'page' : undefined}
            onClick={() => navegar(item.rota)}
          >
            {item.rotulo}
          </button>
        ))}
      </nav>
      <div className="cabecalho__espaco" />
      <form role="search" onSubmit={buscar}>
        <label className="cabecalho__busca">
          <span className="oculto-visual">Buscar aluno</span>
          <Icone nome="search" />
          <input
            type="search"
            value={busca}
            placeholder="Buscar aluno"
            onChange={(evento) => setBusca(evento.target.value)}
          />
        </label>
      </form>
      <button
        type="button"
        className="cabecalho__professor"
        aria-expanded={menuAberto}
        aria-haspopup="menu"
        onClick={() => setMenuAberto((aberto) => !aberto)}
      >
        <span className="iniciais" aria-hidden="true">
          {iniciais(nome)}
        </span>
        <span className="cabecalho__professor-nome">{nome}</span>
        <Icone nome="expand_more" tamanho="medio" />
      </button>
      {menuAberto ? <MenuProfessor aoFechar={() => setMenuAberto(false)} /> : null}
    </header>
  );
}
