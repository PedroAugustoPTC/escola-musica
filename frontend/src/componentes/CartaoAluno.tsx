import { useNavigate } from 'react-router';
import { msg } from '../mensagens/mensagens';
import type { AlunoResumoDto } from '../servicos/alunos';
import { formatarData } from '../servicos/formatacao';
import { Botao } from './Botao';
import { Icone } from './Icone';

export function descricaoAluno(aluno: AlunoResumoDto): string {
  const partes: string[] = [];
  if (aluno.instrumento) partes.push(aluno.instrumento);
  if (aluno.ultimaAulaEm) partes.push(msg('ULTIMA_AULA', { data: formatarData(aluno.ultimaAulaEm) }));
  return partes.join(' · ');
}

/** Rota de "Registrar aula": com relatório em aberto, a tela 04 decide continuar ou descartar. */
export function rotaRegistrarAula(aluno: AlunoResumoDto): string {
  return aluno.possuiRelatorioEmAberto
    ? `/alunos/${aluno.id}/relatorio-em-aberto`
    : `/alunos/${aluno.id}/nova-aula`;
}

/** Cartão de aluno da tela 03 com as ações Registrar aula, Relatório semestral e Histórico. */
export function CartaoAluno({ aluno }: { aluno: AlunoResumoDto }) {
  const navegar = useNavigate();
  const descricao = descricaoAluno(aluno);
  return (
    <article className="cartao">
      <div className="cartao-aluno__topo">
        <div className="cartao-aluno__identidade">
          <h2 className="titulo-cartao">{aluno.nome}</h2>
          {descricao ? <div className="texto-apoio">{descricao}</div> : null}
        </div>
        {aluno.possuiRelatorioEmAberto ? (
          <span className="chip chip--aviso">
            <Icone nome="edit_note" tamanho="pequeno" />
            {msg('MARCA_EM_ABERTO')}
          </span>
        ) : null}
      </div>
      <div className="cartao-aluno__acoes">
        <Botao
          className="cartao-aluno__principal"
          icone="mic"
          aria-label={`${msg('BOTAO_REGISTRAR_AULA')} — ${aluno.nome}`}
          onClick={() => navegar(rotaRegistrarAula(aluno))}
        >
          {msg('BOTAO_REGISTRAR_AULA')}
        </Botao>
        <Botao
          variante="secundario"
          tamanho="compacto"
          aria-label={`${msg('BOTAO_RELATORIO_SEMESTRAL')} — ${aluno.nome}`}
          onClick={() => navegar(`/alunos/${aluno.id}/semestral`)}
        >
          {msg('BOTAO_RELATORIO_SEMESTRAL')}
        </Botao>
        <Botao
          variante="secundario"
          tamanho="compacto"
          aria-label={`${msg('BOTAO_HISTORICO')} — ${aluno.nome}`}
          onClick={() => navegar(`/alunos/${aluno.id}/historico`)}
        >
          {msg('BOTAO_HISTORICO')}
        </Botao>
      </div>
    </article>
  );
}
