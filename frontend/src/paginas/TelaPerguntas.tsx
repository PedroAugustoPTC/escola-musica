import { useEffect, useId, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { Botao } from '../componentes/Botao';
import { DialogoConfirmacao } from '../componentes/DialogoConfirmacao';
import { EstadoCarregando } from '../componentes/EstadoCarregando';
import { AlertaErro, EstadoErro, textoDoErro } from '../componentes/EstadoErro';
import { Estrutura } from '../componentes/Estrutura';
import { GravadorAudio } from '../componentes/GravadorAudio';
import { Icone } from '../componentes/Icone';
import { msg } from '../mensagens/mensagens';
import { formatarData } from '../servicos/formatacao';
import { useInicio } from '../servicos/inicio';
import { relatoriosAula, useRelatorioAula, type AudioSelecionado } from '../servicos/relatoriosAula';
import type { ResultadoNavegacao } from './TelaProcessando';

/** Rota da etapa atual de um relatório de aula (usada quando a tela aberta não é a da etapa). */
export function rotaDaEtapa(relatorio: {
  relatorioId: string;
  status: string;
  trabalhoEmCursoId?: string | null;
}): string {
  if (relatorio.trabalhoEmCursoId) return `/trabalhos/${relatorio.trabalhoEmCursoId}`;
  switch (relatorio.status) {
    case 'AGUARDANDO_RESPOSTAS':
      return `/relatorios-aula/${relatorio.relatorioId}/perguntas`;
    case 'APROVADO':
      return `/relatorios-aula/${relatorio.relatorioId}/aprovado`;
    case 'CANCELADO':
      return '/';
    default:
      return `/relatorios-aula/${relatorio.relatorioId}`;
  }
}

/** Tela 07 — Perguntas pendentes: resposta única por texto ou por áudio (FR-024, FR-024a). */
export function TelaPerguntas() {
  const { relatorioId } = useParams();
  const navegar = useNavigate();
  const local = useLocation();
  const resultado = (local.state as ResultadoNavegacao | null)?.resultado ?? null;
  const relatorio = useRelatorioAula(relatorioId);
  const inicio = useInicio();
  const idResposta = useId();
  const [texto, setTexto] = useState('');
  const [audio, setAudio] = useState<AudioSelecionado | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<unknown>(null);
  const [erroLocal, setErroLocal] = useState<string | null>(null);
  const [confirmarCancelamento, setConfirmarCancelamento] = useState(false);
  const [cancelando, setCancelando] = useState(false);
  const [erroCancelamento, setErroCancelamento] = useState<unknown>(null);

  const dados = relatorio.data;
  useEffect(() => {
    if (dados && (dados.status !== 'AGUARDANDO_RESPOSTAS' || dados.trabalhoEmCursoId)) {
      navegar(rotaDaEtapa(dados), { replace: true });
    }
  }, [dados, navegar]);

  const contexto = {
    sobretitulo: 'Relatório de aula',
    titulo: dados?.alunoNome ?? '',
    voltarPara: '/',
  };

  if (relatorio.isPending) {
    return (
      <Estrutura contexto={contexto}>
        <EstadoCarregando rotulo="Carregando as perguntas" />
      </Estrutura>
    );
  }
  if (relatorio.isError) {
    return (
      <Estrutura contexto={contexto}>
        <EstadoErro erro={relatorio.error} aoTentarDeNovo={() => void relatorio.refetch()} />
      </Estrutura>
    );
  }
  if (!dados) return null;

  const enviar = async () => {
    if (!relatorioId) return;
    if (!audio && !texto.trim()) {
      setErroLocal(msg('RESPOSTA_VAZIA'));
      return;
    }
    setErroLocal(null);
    setErro(null);
    setEnviando(true);
    try {
      const { trabalhoId } = audio
        ? await relatoriosAula.responderAudio(relatorioId, audio)
        : await relatoriosAula.responderTexto(relatorioId, texto.trim());
      navegar(`/trabalhos/${trabalhoId}`);
    } catch (falha) {
      setErro(falha);
      setEnviando(false);
    }
  };

  const cancelar = async () => {
    if (!relatorioId) return;
    setCancelando(true);
    setErroCancelamento(null);
    try {
      await relatoriosAula.cancelar(relatorioId);
      navegar('/', { replace: true });
    } catch (falha) {
      setErroCancelamento(falha);
      setCancelando(false);
    }
  };

  return (
    <Estrutura
      contexto={contexto}
      barraEstreita
      barraAcoes={
        <>
          <Botao variante="link-perigo" onClick={() => setConfirmarCancelamento(true)} disabled={enviando}>
            {msg('BOTAO_CANCELAR_RELATORIO')}
          </Botao>
          <Botao tamanho="grande" icone="send" carregando={enviando} onClick={() => void enviar()}>
            Enviar resposta
          </Botao>
        </>
      }
    >
      <div className="pagina pagina--estreita">
        <div>
          <div className="sobretitulo">Relatório de aula · {formatarData(dados.dataAula)}</div>
          <h1 className="titulo-pagina">Faltam algumas informações</h1>
          <p className="subtitulo">
            Faltam algumas informações para concluir o relatório de {dados.alunoNome}. Responda com suas
            palavras, tudo em uma única mensagem.
          </p>
        </div>
        {resultado === 'FALHA_RESPOSTA' ? (
          <div role="alert" className="alerta alerta--erro">
            <Icone nome="error" />
            <div className="alerta__texto">{msg('FALHA_RESPOSTA')}</div>
          </div>
        ) : null}
        <ol className="perguntas">
          {dados.perguntasPendentes.map((pergunta, indice) => (
            <li key={`${indice}-${pergunta}`} className="perguntas__item">
              <span className="perguntas__numero">{indice + 1}</span>
              <span>{pergunta}</span>
            </li>
          ))}
        </ol>
        <div className="campo">
          <label htmlFor={idResposta} className="campo__rotulo">
            Sua resposta
          </label>
          <textarea
            id={idResposta}
            className="area-texto"
            rows={5}
            value={texto}
            maxLength={4000}
            disabled={enviando || audio !== null}
            aria-describedby={`${idResposta}-ajuda`}
            aria-invalid={erroLocal ? true : undefined}
            placeholder="Ex.: Ele estudou em casa três vezes. Para a próxima aula, pedi a escala de Sol maior."
            onChange={(evento) => setTexto(evento.target.value)}
          />
          <div id={`${idResposta}-ajuda`} className="campo__ajuda">
            Você pode responder a todas as perguntas de uma vez, digitando ou gravando.
          </div>
          {erroLocal ? (
            <div role="alert" className="campo__erro">
              <Icone nome="error" />
              {erroLocal}
            </div>
          ) : null}
        </div>
        <GravadorAudio
          compacto
          limiteMb={inicio.data?.limiteAudioMb ?? 50}
          aoMudar={setAudio}
          desabilitado={enviando}
        />
        {erro ? <AlertaErro erro={erro} /> : null}
      </div>
      <DialogoConfirmacao
        aberto={confirmarCancelamento}
        perigo
        icone="block"
        titulo="Cancelar este relatório?"
        texto={msg('CONFIRMAR_CANCELAMENTO')}
        rotuloConfirmar={msg('BOTAO_CONFIRMAR_CANCELAMENTO')}
        carregando={cancelando}
        erro={erroCancelamento ? textoDoErro(erroCancelamento) : undefined}
        aoConfirmar={() => void cancelar()}
        aoFechar={() => {
          setConfirmarCancelamento(false);
          setErroCancelamento(null);
        }}
      />
    </Estrutura>
  );
}
