import { msg } from '../mensagens/mensagens';
import type { RelatorioAulaDto } from '../servicos/relatoriosAula';

type Conteudo = RelatorioAulaDto['conteudo'];

interface Secao {
  titulo: string;
  itens?: string[];
  texto?: string;
}

/** As cinco seções do relatório de aula (FR-019), na ordem do modelo. */
export function secoesDaAula(conteudo: Conteudo): Secao[] {
  return [
    { titulo: msg('SECAO_CONTEUDOS_TRABALHADOS'), itens: conteudo.conteudosTrabalhados },
    { titulo: msg('SECAO_EVOLUCAO'), texto: conteudo.evolucao },
    { titulo: msg('SECAO_DIFICULDADES'), itens: conteudo.dificuldades },
    { titulo: msg('SECAO_ATIVIDADES_PROPOSTAS'), itens: conteudo.atividadesPropostas },
    { titulo: msg('SECAO_OBSERVACOES'), texto: conteudo.observacoes },
  ];
}

/** Relatório em texto (tela 08): campos sem informação aparecem como "—" (FR-020). */
export function SecoesRelatorio({
  conteudo,
  esmaecido = false,
}: {
  conteudo: Conteudo;
  esmaecido?: boolean;
}) {
  return (
    <article aria-label="Relatório em texto" className={`secoes${esmaecido ? ' secoes--esmaecido' : ''}`}>
      {secoesDaAula(conteudo).map((secao) => {
        const vazia = secao.itens ? secao.itens.length === 0 : !secao.texto?.trim();
        return (
          <section key={secao.titulo} className="secoes__secao">
            <h2 className="secoes__titulo">{secao.titulo}</h2>
            {vazia ? (
              <p className="secoes__vazia">{msg('SECAO_VAZIA')}</p>
            ) : secao.itens ? (
              <ul className="secoes__lista">
                {secao.itens.map((item, indice) => (
                  <li key={`${indice}-${item}`}>
                    <span className="secoes__marcador" aria-hidden="true" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="secoes__texto">{secao.texto}</p>
            )}
          </section>
        );
      })}
    </article>
  );
}
