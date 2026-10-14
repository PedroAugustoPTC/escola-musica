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
  if (relatorio.type === 'SEMESTER' && relatorio.reportId) {
    return `/relatorios-semestrais/${relatorio.reportId}`;
  }
  return `/alunos/${relatorio.studentId}/relatorio-em-aberto`;
}

function situacaoDoRelatorio(relatorio: RelatorioEmAbertoDto): string {
  return relatorio.status ? msg(`STATUS_${relatorio.status}`) : msg('STATUS_PROCESSING');
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
  const semAlunos = dados.totalStudents === 0;

  return (
    <Estrutura navAtiva="painel" navInferior>
      <div className="pagina pagina--larga">
        <div>
          <div className="sobretitulo">Painel</div>
          <h1 className="titulo-pagina">{msg('GREETING', { name: primeiroNome(dados.teacher.name) })}</h1>
          <p className="subtitulo painel__subtitulo">{msg('HOME_SUBTITLE')}</p>
        </div>
        {semAlunos ? (
          <EstadoVazio monograma solido titulo="Nenhum aluno por aqui" texto={msg('NO_STUDENTS')} />
        ) : (
          <>
            {dados.openReports.map((relatorio) => (
              <div
                key={`${relatorio.reportId ?? relatorio.jobId}`}
                role="status"
                className="alerta alerta--aviso painel__em-aberto"
              >
                <Icone nome="edit_note" />
                <div className="alerta__texto">
                  <strong>{msg('HOME_OPEN_REPORTS')}</strong>
                  <div>
                    {relatorio.studentName} · {msg(`TYPE_${relatorio.type}`)} · {situacaoDoRelatorio(relatorio)}{' '}
                    · última alteração em {dataDoInstante(relatorio.updatedAt)}{' '}
                    {horaDoInstante(relatorio.updatedAt)}
                  </div>
                </div>
                <Botao
                  variante="aviso"
                  tamanho="compacto"
                  aria-label={`${msg('CONTINUE_REPORT_BUTTON', {
                    student: relatorio.studentName,
                    data: dataDoInstante(relatorio.updatedAt),
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
                  <span className="painel__registrar-titulo">{msg('REGISTER_LESSON_BUTTON')}</span>
                  <span className="painel__registrar-apoio">Grave um áudio contando como foi a aula.</span>
                </span>
              </button>
              <div className="cartao painel__total">
                <div className="rotulo-secao painel__total-rotulo">Seus alunos</div>
                <div className="painel__total-numero">
                  <span className="painel__total-valor">{dados.totalStudents}</span>
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
                {dados.students.map((aluno) => (
                  <button
                    key={aluno.id}
                    type="button"
                    className="lista-alunos__item"
                    onClick={() => navegar(`/alunos/${aluno.id}/historico`)}
                  >
                    <span className="iniciais iniciais--grande" aria-hidden="true">
                      {iniciais(aluno.name)}
                    </span>
                    <span className="lista-alunos__texto">
                      <span className="lista-alunos__nome">{aluno.name}</span>
                      <span className="texto-apoio">{descricaoAluno(aluno)}</span>
                    </span>
                    {aluno.hasOpenReport ? (
                      <span className="chip chip--aviso">{msg('OPEN_REPORT_MARK')}</span>
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
