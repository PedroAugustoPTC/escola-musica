import type { ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { useEhMovel } from '../app/useEhMovel';
import { Cabecalho, ITENS_NAVEGACAO, type ContextoCabecalho, type ItemNavegacao } from './Cabecalho';
import { Icone } from './Icone';

/**
 * Moldura das telas autenticadas: cabeçalho, conteúdo rolável, barra de ações fixa (gravação,
 * perguntas, conferência, semestral), navegação inferior no celular e rodapé no computador.
 */
export function Estrutura({
  contexto,
  navAtiva,
  navInferior = false,
  barraAcoes,
  barraEstreita = false,
  children,
}: {
  contexto?: ContextoCabecalho;
  navAtiva?: ItemNavegacao;
  navInferior?: boolean;
  barraAcoes?: ReactNode;
  barraEstreita?: boolean;
  children: ReactNode;
}) {
  const movel = useEhMovel();
  const navegar = useNavigate();
  return (
    <div className="app">
      <a className="oculto-visual" href="#conteudo">
        Ir para o conteúdo
      </a>
      <Cabecalho contexto={contexto} navAtiva={navAtiva} />
      <main id="conteudo" className="app__principal" tabIndex={-1}>
        {children}
        {!movel && !barraAcoes ? (
          <footer className="rodape">
            <span className="rodape__marca">INSTITUTO MUSICAL FELIPE BLAFT</span>
            <span>Portal do Professor · Relatórios pedagógicos</span>
          </footer>
        ) : null}
      </main>
      {barraAcoes ? (
        <div className="barra-acoes">
          <div className={`barra-acoes__conteudo${barraEstreita ? ' barra-acoes__conteudo--estreita' : ''}`}>
            {barraAcoes}
          </div>
        </div>
      ) : null}
      {movel && navInferior ? (
        <nav aria-label="Principal" className="nav-inferior">
          {ITENS_NAVEGACAO.map((item) => (
            <button
              key={item.chave}
              type="button"
              className="nav-inferior__item"
              aria-current={navAtiva === item.chave ? 'page' : undefined}
              onClick={() => navegar(item.rota)}
            >
              <Icone nome={item.icone} />
              {item.rotulo}
            </button>
          ))}
        </nav>
      ) : null}
    </div>
  );
}
