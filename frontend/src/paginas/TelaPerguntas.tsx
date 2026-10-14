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
  reportId: string;
  status: string;
  activeJobId?: string | null;
}): string {
  if (relatorio.activeJobId) return `/trabalhos/${relatorio.activeJobId}`;
  switch (relatorio.status) {
    case 'AWAITING_ANSWERS':
      return `/relatorios-aula/${relatorio.reportId}/perguntas`;
    case 'APPROVED':
      return `/relatorios-aula/${relatorio.reportId}/aprovado`;
    case 'CANCELLED':
      return '/';
    default:
      return `/relatorios-aula/${relatorio.reportId}`;
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
    if (dados && (dados.status !== 'AWAITING_ANSWERS' || dados.activeJobId)) {
      navegar(rotaDaEtapa(dados), { replace: true });
    }
  }, [dados, navegar]);

  const contexto = {
    sobretitulo: 'Relatório de aula',
    titulo: dados?.studentName ?? '',
    voltarPara: '/',
  };

  if (relatorio.isPending) {
    return (
      <Estrutura navAtiva="alunos" contexto={contexto}>
        <EstadoCarregando rotulo="Carregando as perguntas" />
      </Estrutura>
    );
  }
  if (relatorio.isError) {
    return (
      <Estrutura navAtiva="alunos" contexto={contexto}>
        <EstadoErro erro={relatorio.error} aoTentarDeNovo={() => void relatorio.refetch()} />
      </Estrutura>
    );
  }
  if (!dados) return null;

  const enviar = async () => {
    if (!relatorioId) return;
    if (!audio && !texto.trim()) {
      setErroLocal(msg('EMPTY_ANSWER'));
      return;
    }
    setErroLocal(null);
    setErro(null);
    setEnviando(true);
    try {
      const { jobId } = audio
        ? await relatoriosAula.responderAudio(relatorioId, audio)
        : await relatoriosAula.responderTexto(relatorioId, texto.trim());
      navegar(`/trabalhos/${jobId}`);
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
      navAtiva="alunos"
      contexto={contexto}
      barraEstreita
      barraAcoes={
        <>
          <Botao variante="link-perigo" onClick={() => setConfirmarCancelamento(true)} disabled={enviando}>
            {msg('CANCEL_REPORT_BUTTON')}
          </Botao>
          <Botao tamanho="grande" icone="send" carregando={enviando} onClick={() => void enviar()}>
            Enviar resposta
          </Botao>
        </>
      }
    >
      <div className="pagina pagina--estreita">
        <div>
          <div className="sobretitulo">Relatório de aula · {formatarData(dados.lessonDate)}</div>
          <h1 className="titulo-pagina">Faltam algumas informações</h1>
          <p className="subtitulo">
            Faltam algumas informações para concluir o relatório de {dados.studentName}. Responda com suas
            palavras, tudo em uma única mensagem.
          </p>
        </div>
        {resultado === 'ANSWER_FAILED' ? (
          <div role="alert" className="alerta alerta--erro">
            <Icone nome="error" />
            <div className="alerta__texto">{msg('ANSWER_FAILED')}</div>
          </div>
        ) : null}
        <ol className="perguntas">
          {dados.pendingQuestions.map((pergunta, indice) => (
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
          limiteMb={inicio.data?.audioLimitMb ?? 50}
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
        texto={msg('CONFIRM_CANCELLATION')}
        rotuloConfirmar={msg('CONFIRM_CANCELLATION_BUTTON')}
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
