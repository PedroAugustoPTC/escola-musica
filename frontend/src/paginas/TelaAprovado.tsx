import { useNavigate, useParams } from 'react-router';
import { useTema } from '../app/TemaProvedor';
import { Botao } from '../componentes/Botao';
import { EstadoCarregando } from '../componentes/EstadoCarregando';
import { EstadoErro } from '../componentes/EstadoErro';
import { Estrutura } from '../componentes/Estrutura';
import { Icone } from '../componentes/Icone';
import { msg } from '../mensagens/mensagens';
import { dataDoInstante, formatarData, horaDoInstante } from '../servicos/formatacao';
import { useRelatorioAula } from '../servicos/relatoriosAula';
import { useRelatorioSemestral } from '../servicos/semestral';

/** Tela 09 — Relatório aprovado (09b modo escuro): data e hora da aprovação e próximos passos. */
export function TelaAprovado({ semestral = false }: { semestral?: boolean }) {
  const { relatorioId } = useParams();
  const aula = useRelatorioAula(semestral ? undefined : relatorioId);
  const relatorioSemestral = useRelatorioSemestral(semestral ? relatorioId : undefined);
  const relatorio = semestral ? relatorioSemestral : aula;
  const navegar = useNavigate();
  const { tintaOuro } = useTema();
  const contexto = {
    sobretitulo: semestral ? 'Relatório semestral' : 'Relatório de aula',
    titulo: 'Aprovado',
    voltarPara: '/',
  };

  if (relatorio.isPending) {
    return (
      <Estrutura contexto={contexto}>
        <EstadoCarregando rotulo="Carregando o relatório" />
      </Estrutura>
    );
  }
  if (relatorio.isError) {
    return (
      <Estrutura contexto={contexto}>
        <EstadoErro erro={relatorio.error} />
      </Estrutura>
    );
  }

  const dados = relatorio.data;
  if (!dados) return null;
  const alunoNome = 'alunoNome' in dados ? dados.alunoNome : dados.informacoesGerais.nome;
  const aprovadoEm = dados.aprovadoEm ?? dados.atualizadoEm;
  const data = dataDoInstante(aprovadoEm);
  const hora = horaDoInstante(aprovadoEm);
  // como no protótipo: a aula pela data; o semestral pelo período
  const referencia =
    'dataAula' in dados ? formatarData(dados.dataAula) : dados.informacoesGerais.periodoRotulo;

  return (
    <Estrutura contexto={contexto}>
      <div className="pagina pagina--aprovado">
        <span className="circulo-icone circulo-icone--ok">
          <Icone nome="check" tamanho="grande" />
        </span>
        <div className="sobretitulo">
          {semestral ? 'Relatório semestral' : 'Relatório de aula'} · {referencia}
        </div>
        <h1 role="status" className="titulo-pagina titulo-pagina--maior">
          Relatório aprovado
        </h1>
        <img src={`/marca/fb-filete-${tintaOuro}.png`} alt="" className="aprovado__filete" />
        <p className="subtitulo">{msg('RELATORIO_APROVADO', { data, hora })}</p>
        <div className="aprovado__dados">
          <div>
            <div className="aprovado__rotulo">ALUNO</div>
            <div className="aprovado__valor">{alunoNome}</div>
          </div>
          <div>
            <div className="aprovado__rotulo">APROVADO EM</div>
            <div className="aprovado__valor">
              {data} {hora}
            </div>
          </div>
        </div>
        <div className="aprovado__acoes">
          <Botao tamanho="grande" bloco onClick={() => navegar('/alunos?registrar=1')}>
            {msg('BOTAO_REGISTRAR_OUTRA_AULA')}
          </Botao>
          <div className="aprovado__acoes-secundarias">
            <Botao variante="secundario" onClick={() => navegar(`/alunos/${dados.alunoId}/historico`)}>
              Ver histórico
            </Botao>
            <Botao variante="secundario" onClick={() => navegar('/alunos')}>
              Voltar para alunos
            </Botao>
          </div>
        </div>
      </div>
    </Estrutura>
  );
}
