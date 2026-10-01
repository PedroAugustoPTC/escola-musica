import { msg } from '../mensagens/mensagens';
import type { SecaoSemestral } from '../servicos/semestral';

/**
 * As 10 seções do semestral (tela 11): a seção 1 (Informações Gerais) é preenchida pelo sistema e
 * só leitura; campos sem informação aparecem como "—" (FR-020, FR-044).
 */
export function SecoesSemestral({
  secoes,
  esmaecido = false,
}: {
  secoes: SecaoSemestral[];
  esmaecido?: boolean;
}) {
  return (
    <article aria-label="Relatório em texto" className={`secoes${esmaecido ? ' secoes--esmaecido' : ''}`}>
      {secoes.map((secao) => (
        <section key={secao.numero} id={`secao-${secao.numero}`} className="secoes__secao">
          <h2 className="secoes__titulo secoes__titulo--numerado">
            <span className="secoes__numero">{secao.numero}</span>
            {secao.titulo}
          </h2>
          {secao.campos.map((campo, indice) => {
            const vazio = campo.itens.length === 0 && !campo.texto.trim();
            return (
              <div key={`${secao.numero}-${indice}`} className="secoes__campo">
                {campo.rotulo ? <div className="secoes__rotulo">{campo.rotulo}</div> : null}
                {vazio ? (
                  <p className="secoes__vazia">{msg('SECAO_VAZIA')}</p>
                ) : campo.itens.length > 0 ? (
                  <ul className="secoes__lista">
                    {campo.itens.map((item, i) => (
                      <li key={`${i}-${item}`}>
                        <span className="secoes__marcador" aria-hidden="true" />
                        <span>{item}</span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="secoes__texto">{campo.texto}</p>
                )}
              </div>
            );
          })}
        </section>
      ))}
    </article>
  );
}
