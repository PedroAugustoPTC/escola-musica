import { useNavigate } from 'react-router';
import { msg } from '../mensagens/mensagens';
import type { AlunoResumoDto } from '../servicos/alunos';
import { formatarData } from '../servicos/formatacao';
import { Botao } from './Botao';
import { Icone } from './Icone';

export function descricaoAluno(aluno: AlunoResumoDto): string {
  const partes: string[] = [];
  if (aluno.instrument) partes.push(aluno.instrument);
  if (aluno.lastLessonAt) partes.push(msg('LAST_LESSON', { data: formatarData(aluno.lastLessonAt) }));
  return partes.join(' · ');
}

/** Rota de "Registrar aula": com relatório em aberto, a tela 04 decide continuar ou descartar. */
export function rotaRegistrarAula(aluno: AlunoResumoDto): string {
  return aluno.hasOpenReport
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
          <h2 className="titulo-cartao">{aluno.name}</h2>
          {descricao ? <div className="texto-apoio">{descricao}</div> : null}
        </div>
        {aluno.hasOpenReport ? (
          <span className="chip chip--aviso">
            <Icone nome="edit_note" tamanho="pequeno" />
            {msg('OPEN_REPORT_MARK')}
          </span>
        ) : null}
      </div>
      <div className="cartao-aluno__acoes">
        <Botao
          className="cartao-aluno__principal"
          icone="mic"
          aria-label={`${msg('REGISTER_LESSON_BUTTON')} — ${aluno.name}`}
          onClick={() => navegar(rotaRegistrarAula(aluno))}
        >
          {msg('REGISTER_LESSON_BUTTON')}
        </Botao>
        <Botao
          variante="secundario"
          tamanho="compacto"
          aria-label={`${msg('SEMESTER_REPORT_BUTTON')} — ${aluno.name}`}
          onClick={() => navegar(`/alunos/${aluno.id}/semestral`)}
        >
          {msg('SEMESTER_REPORT_BUTTON')}
        </Botao>
        <Botao
          variante="secundario"
          tamanho="compacto"
          aria-label={`${msg('HISTORY_BUTTON')} — ${aluno.name}`}
          onClick={() => navegar(`/alunos/${aluno.id}/historico`)}
        >
          {msg('HISTORY_BUTTON')}
        </Botao>
      </div>
    </article>
  );
}
