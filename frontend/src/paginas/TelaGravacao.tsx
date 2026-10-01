import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { Botao } from '../componentes/Botao';
import { EstadoCarregando } from '../componentes/EstadoCarregando';
import { AlertaErro, EstadoErro } from '../componentes/EstadoErro';
import { Estrutura } from '../componentes/Estrutura';
import { GravadorAudio } from '../componentes/GravadorAudio';
import { msg } from '../mensagens/mensagens';
import { useAluno } from '../servicos/alunos';
import { ErroApi } from '../servicos/clienteApi';
import { formatarData, hojeNaEscola } from '../servicos/formatacao';
import { useInicio } from '../servicos/inicio';
import { relatoriosAula, type AudioSelecionado } from '../servicos/relatoriosAula';

const LIMITE_PADRAO_MB = 50;

/** Tela 05 — Gravação (05b gravando, 05c concluída, 05d só arquivo, 05e modo escuro). */
export function TelaGravacao() {
  const { alunoId } = useParams();
  const aluno = useAluno(alunoId);
  const inicio = useInicio();
  const navegar = useNavigate();
  const [audio, setAudio] = useState<AudioSelecionado | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<unknown>(null);
  const limiteMb = inicio.data?.limiteAudioMb ?? LIMITE_PADRAO_MB;

  const contexto = {
    sobretitulo: 'Novo relatório',
    titulo: aluno.data?.nome ?? '',
    voltarPara: '/alunos?registrar=1',
  };

  if (aluno.isPending) {
    return (
      <Estrutura contexto={contexto}>
        <EstadoCarregando rotulo="Carregando o aluno" />
      </Estrutura>
    );
  }
  if (aluno.isError) {
    return (
      <Estrutura contexto={contexto}>
        <EstadoErro erro={aluno.error} />
      </Estrutura>
    );
  }

  const enviar = async () => {
    if (!audio || !alunoId) return;
    setEnviando(true);
    setErro(null);
    try {
      const { trabalhoId } = await relatoriosAula.registrar(alunoId, audio);
      navegar(`/trabalhos/${trabalhoId}`, { state: { alunoId } });
    } catch (falha) {
      if (falha instanceof ErroApi && falha.codigo === 'RELATORIO_EM_ABERTO') {
        // FR-034: o aluno já tem relatório em aberto; a tela 04 oferece continuar ou descartar
        navegar(`/alunos/${alunoId}/relatorio-em-aberto`, { replace: true });
        return;
      }
      setErro(falha);
      setEnviando(false);
    }
  };

  return (
    <Estrutura
      contexto={contexto}
      barraEstreita
      barraAcoes={
        <Botao
          tamanho="grande"
          bloco
          icone="send"
          disabled={!audio}
          carregando={enviando}
          onClick={() => void enviar()}
        >
          Enviar áudio e gerar relatório
        </Botao>
      }
    >
      <div className="pagina pagina--estreita">
        <div>
          <div className="sobretitulo">Novo relatório de aula</div>
          <h1 className="titulo-pagina">{aluno.data.nome}</h1>
          <p className="subtitulo">Aula de {formatarData(hojeNaEscola())}</p>
        </div>
        {erro ? <AlertaErro erro={erro} /> : null}
        <GravadorAudio
          limiteMb={limiteMb}
          aoMudar={setAudio}
          desabilitado={enviando}
          dica="Conte o que foi trabalhado, como o aluno evoluiu, as dificuldades e o que ele deve estudar em casa."
        />
        <p className="texto-apoio">{msg('FORMATOS_E_LIMITE', { limiteMb })}</p>
      </div>
    </Estrutura>
  );
}
