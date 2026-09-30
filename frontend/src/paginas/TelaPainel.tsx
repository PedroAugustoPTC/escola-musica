import { useNavigate } from 'react-router';
import { descricaoAluno } from '../componentes/CartaoAluno';
import { iniciais } from '../componentes/Cabecalho';
import { Botao } from '../componentes/Botao';
import { EstadoCarregando } from '../componentes/EstadoCarregando';
import { AlertaErro } from '../componentes/EstadoErro';
import { EstadoVazio } from '../componentes/EstadoVazio';
import { Estrutura } from '../componentes/Estrutura';
import { Icone } from '../componentes/Icone';
import { msg } from '../mensagens/mensagens';
import { dataDoInstante, horaDoInstante, primeiroNome } from '../servicos/formatacao';
import { useInicio, type RelatorioEmAbertoDto } from '../servicos/inicio';

/** Destino de "Continuar" para um relatório em aberto (US3). */
export function rotaContinuar(relatorio: RelatorioEmAbertoDto): string {
  if (relatorio.tipo === 'SEMESTRAL' && relatorio.relatorioId) {
    return `/relatorios-semestrais/${relatorio.relatorioId}`;
  }
  return `/alunos/${relatorio.alunoId}/relatorio-em-aberto`;
}

function situacaoDoRelatorio(relatorio: RelatorioEmAbertoDto): string {
  return relatorio.status ? msg(`STATUS_${relatorio.status}`) : msg('STATUS_EM_PROCESSAMENTO');
}

/** Tela 02 — Painel (02b vazio, 02c menu, 02d modo escuro, relatórios em aberto com "Continuar"). */
export function TelaPainel() {
  const inicio = useInicio();
  const navegar = useNavigate();

  if (inicio.isPending) {
    return (
      <Estrutura navAtiva="painel" navInferior>
        <EstadoCarregando rotulo="Carregando o painel" />
      </Estrutura>
    );
  }
  if (inicio.isError) {
    return (
      <Estrutura navAtiva="painel" navInferior>
        <div className="pagina pagina--larga">
          <AlertaErro erro={inicio.error} aoTentarDeNovo={() => inicio.refetch()} />
        </div>
      </Estrutura>
    );
  }

  const dados = inicio.data;
  const semAlunos = dados.totalAlunos === 0;

  return (
    <Estrutura navAtiva="painel" navInferior>
      <div className="pagina pagina--larga">
        <div>
          <div className="sobretitulo">Painel</div>
          <h1 className="titulo-pagina">{msg('SAUDACAO', { nome: primeiroNome(dados.professor.nome) })}</h1>
          <p className="subtitulo painel__subtitulo">{msg('INICIO_SUBTITULO')}</p>
        </div>
        {semAlunos ? (
          <EstadoVazio monograma solido titulo="Nenhum aluno por aqui" texto={msg('SEM_ALUNOS')} />
        ) : (
          <>
            {dados.relatoriosEmAberto.map((relatorio) => (
              <div
                key={`${relatorio.relatorioId ?? relatorio.trabalhoId}`}
                role="status"
                className="alerta alerta--aviso painel__em-aberto"
              >
                <Icone nome="edit_note" />
                <div className="alerta__texto">
                  <strong>{msg('INICIO_RELATORIOS_EM_ABERTO')}</strong>
                  <div>
                    {relatorio.alunoNome} · {msg(`TIPO_${relatorio.tipo}`)} · {situacaoDoRelatorio(relatorio)}{' '}
                    · última alteração em {dataDoInstante(relatorio.atualizadoEm)}{' '}
                    {horaDoInstante(relatorio.atualizadoEm)}
                  </div>
                </div>
                <Botao
                  variante="aviso"
                  tamanho="compacto"
                  aria-label={`${msg('BOTAO_CONTINUAR_RELATORIO', {
                    aluno: relatorio.alunoNome,
                    data: dataDoInstante(relatorio.atualizadoEm),
                  })}`}
                  onClick={() => navegar(rotaContinuar(relatorio))}
                >
                  Retomar relatório
                </Botao>
              </div>
            ))}
            <div className="painel__destaques">
              <button
                type="button"
                className="painel__registrar"
                onClick={() => navegar('/alunos?registrar=1')}
              >
                <span className="painel__registrar-icone">
                  <Icone nome="mic" tamanho="grande" />
                </span>
                <span className="painel__registrar-texto">
                  <span className="painel__registrar-titulo">{msg('BOTAO_REGISTRAR_AULA')}</span>
                  <span className="painel__registrar-apoio">Grave um áudio contando como foi a aula.</span>
                </span>
              </button>
              <div className="cartao painel__total">
                <div className="rotulo-secao painel__total-rotulo">Seus alunos</div>
                <div className="painel__total-numero">
                  <span className="painel__total-valor">{dados.totalAlunos}</span>
                  <span className="subtitulo">alunos associados</span>
                </div>
                <Botao variante="link" onClick={() => navegar('/alunos')}>
                  Ver todos os alunos
                </Botao>
              </div>
            </div>
            <section aria-labelledby="h-alunos" className="painel__alunos">
              <h2 id="h-alunos" className="rotulo-secao">
                Primeiros alunos
              </h2>
              <div className="lista-alunos">
                {dados.alunos.map((aluno) => (
                  <button
                    key={aluno.id}
                    type="button"
                    className="lista-alunos__item"
                    onClick={() => navegar(`/alunos/${aluno.id}/historico`)}
                  >
                    <span className="iniciais iniciais--grande" aria-hidden="true">
                      {iniciais(aluno.nome)}
                    </span>
                    <span className="lista-alunos__texto">
                      <span className="lista-alunos__nome">{aluno.nome}</span>
                      <span className="texto-apoio">{descricaoAluno(aluno)}</span>
                    </span>
                    {aluno.possuiRelatorioEmAberto ? (
                      <span className="chip chip--aviso">{msg('MARCA_EM_ABERTO')}</span>
                    ) : null}
                    <Icone nome="chevron_right" />
                  </button>
                ))}
              </div>
            </section>
          </>
        )}
      </div>
    </Estrutura>
  );
}
