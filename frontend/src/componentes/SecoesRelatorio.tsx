import { msg } from '../mensagens/mensagens';
import type { RelatorioAulaDto } from '../servicos/relatoriosAula';

type Conteudo = RelatorioAulaDto['content'];

interface Secao {
  titulo: string;
  itens?: string[];
  texto?: string;
}

/** As cinco seções do relatório de aula (FR-019), na ordem do modelo. */
export function secoesDaAula(conteudo: Conteudo): Secao[] {
  return [
    { titulo: msg('WORKED_TOPICS_SECTION'), itens: conteudo.workedTopics },
    { titulo: msg('PROGRESS_SECTION'), texto: conteudo.progress },
    { titulo: msg('DIFFICULTIES_SECTION'), itens: conteudo.difficulties },
    { titulo: msg('PROPOSED_ACTIVITIES_SECTION'), itens: conteudo.proposedActivities },
    { titulo: msg('OBSERVATIONS_SECTION'), texto: conteudo.observations },
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
              <p className="secoes__vazia">{msg('EMPTY_SECTION')}</p>
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
