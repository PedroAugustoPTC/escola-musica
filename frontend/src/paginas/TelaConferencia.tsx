import { useEffect, useId, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate, useParams } from 'react-router';
import { useEhMovel } from '../app/useEhMovel';
import { Botao } from '../componentes/Botao';
import { DialogoConfirmacao } from '../componentes/DialogoConfirmacao';
import { EstadoCarregando } from '../componentes/EstadoCarregando';
import { AlertaErro, EstadoErro, textoDoErro } from '../componentes/EstadoErro';
import { Estrutura } from '../componentes/Estrutura';
import { GravadorAudio } from '../componentes/GravadorAudio';
import { Icone } from '../componentes/Icone';
import { SecoesRelatorio } from '../componentes/SecoesRelatorio';
import { VisualizadorPdf } from '../componentes/VisualizadorPdf';
import { msg } from '../mensagens/mensagens';
import { ErroApi } from '../servicos/clienteApi';
import { formatarData } from '../servicos/formatacao';
import { useInicio } from '../servicos/inicio';
import {
  caminhoPdfConferencia,
  chaveRelatorioAula,
  relatoriosAula,
  useRelatorioAula,
  type AudioSelecionado,
} from '../servicos/relatoriosAula';
import { usePollingTrabalho } from '../servicos/trabalhos';
import { rotaDaEtapa } from './TelaPerguntas';
import type { ResultadoNavegacao } from './TelaProcessando';

type Aba = 'texto' | 'pdf';

interface Aviso {
  tipo: 'ok' | 'info' | 'erro';
  linhas: string[];
}

/** Mensagem mostrada depois de um processamento (contracts/mensagens.md). */
export function avisoDoResultado(
  resultado: string | null,
  textoEntendido: string | null,
  versao: number,
): Aviso | null {
  const entendido = textoEntendido ? [msg('UNDERSTOOD_TEXT', { text: textoEntendido })] : [];
  switch (resultado) {
    case 'NEW_VERSION':
      return { tipo: 'ok', linhas: [msg('CHANGE_APPLIED', { version: versao }), ...entendido] };
    case 'NO_CONTENT_CHANGE':
    case 'ACTION_REQUEST_IGNORED':
      return { tipo: 'info', linhas: [msg(resultado, { version: versao }), ...entendido] };
    case 'CHANGE_FAILED':
      return { tipo: 'erro', linhas: [msg('CHANGE_FAILED', { version: versao }), ...entendido] };
    case 'READY_FOR_REVIEW':
      return textoEntendido
        ? { tipo: 'ok', linhas: [msg('ANSWER_INCORPORATED', { text: textoEntendido })] }
        : null;
    default:
      return null;
  }
}

/**
 * Tela 08 — Conferência (08b PDF sendo gerado, 08c aplicando alteração, 08d versão desatualizada).
 * Abas "Texto"/"Ver PDF" no celular e lado a lado no computador; a aprovação cita a versão
 * apresentada e nada navega sem resposta 2xx (FR-053).
 */
export function TelaConferencia() {
  const { relatorioId } = useParams();
  const navegar = useNavigate();
  const local = useLocation();
  const cliente = useQueryClient();
  const movel = useEhMovel();
  const relatorio = useRelatorioAula(relatorioId);
  const inicio = useInicio();
  const idAjuste = useId();
  const estadoInicial = local.state as ResultadoNavegacao | null;

  const [aba, setAba] = useState<Aba>('texto');
  const [ajuste, setAjuste] = useState('');
  const [audio, setAudio] = useState<AudioSelecionado | null>(null);
  const [chaveGravador, setChaveGravador] = useState(0);
  const [trabalhoAlteracao, setTrabalhoAlteracao] = useState<string | null>(null);
  const [enviandoAjuste, setEnviandoAjuste] = useState(false);
  const [erroAjuste, setErroAjuste] = useState<unknown>(null);
  const [desatualizada, setDesatualizada] = useState(false);
  const [avisoAtual, setAviso] = useState<Aviso | null | undefined>(undefined);
  const [dialogo, setDialogo] = useState<'aprovar' | 'cancelar' | null>(null);
  const [confirmando, setConfirmando] = useState(false);
  const [erroDialogo, setErroDialogo] = useState<unknown>(null);

  const dados = relatorio.data;
  const trabalhoEmCurso = trabalhoAlteracao ?? dados?.activeJobId ?? null;
  const polling = usePollingTrabalho(trabalhoEmCurso ?? undefined);

  // até haver um aviso desta tela, vale o resultado trazido do processamento (respostas incorporadas)
  const aviso =
    avisoAtual !== undefined
      ? avisoAtual
      : dados && estadoInicial?.resultado
        ? avisoDoResultado(estadoInicial.resultado, estadoInicial.textoEntendido, dados.currentVersion)
        : null;

  // relatório que não está em conferência vai para a tela da etapa
  useEffect(() => {
    if (!dados || trabalhoEmCurso) return;
    if (dados.status !== 'AWAITING_REVIEW') {
      navegar(rotaDaEtapa(dados), { replace: true });
    }
  }, [dados, trabalhoEmCurso, navegar]);

  // alteração concluída: recarrega e mostra o resultado (ALTERACAO_APLICADA + TEXTO_ENTENDIDO etc.)
  const concluido = polling.finalizado ? polling.data : undefined;
  useEffect(() => {
    if (!concluido || !trabalhoEmCurso) return;
    void (async () => {
      const atualizado = await cliente.fetchQuery({
        queryKey: chaveRelatorioAula(relatorioId),
        queryFn: () => relatoriosAula.obter(relatorioId!),
        staleTime: 0,
      });
      setAviso(
        avisoDoResultado(
          concluido.resultCode ?? null,
          concluido.understoodText ?? null,
          atualizado.currentVersion,
        ),
      );
      setTrabalhoAlteracao(null);
      setDesatualizada(false);
    })();
  }, [concluido, trabalhoEmCurso, cliente, relatorioId]);

  const contexto = {
    sobretitulo: 'Relatório de aula',
    titulo: dados?.studentName ?? '',
    voltarPara: '/',
  };

  if (relatorio.isPending) {
    return (
      <Estrutura navAtiva="alunos" contexto={contexto}>
        <EstadoCarregando rotulo="Carregando o relatório" />
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

  const aplicando = trabalhoEmCurso !== null;
  const versao = dados.currentVersion;
  const podeAjustar = !aplicando && !enviandoAjuste && (ajuste.trim() !== '' || audio !== null);

  const recarregar = async () => {
    setDesatualizada(false);
    await relatorio.refetch();
  };

  const tratarConflito = (falha: unknown): boolean => {
    if (falha instanceof ErroApi && falha.codigo === 'STALE_VERSION') {
      setDesatualizada(true);
      void relatorio.refetch();
      return true;
    }
    return false;
  };

  const solicitarAlteracao = async () => {
    if (!relatorioId || !podeAjustar) return;
    setEnviandoAjuste(true);
    setErroAjuste(null);
    setAviso(null);
    try {
      const { jobId } = audio
        ? await relatoriosAula.alterarAudio(relatorioId, audio, versao)
        : await relatoriosAula.alterarTexto(relatorioId, ajuste.trim(), versao);
      setTrabalhoAlteracao(jobId);
      setAjuste('');
      setAudio(null);
      setChaveGravador((chave) => chave + 1);
    } catch (falha) {
      if (!tratarConflito(falha)) setErroAjuste(falha);
    } finally {
      setEnviandoAjuste(false);
    }
  };

  const confirmar = async () => {
    if (!relatorioId || !dialogo) return;
    setConfirmando(true);
    setErroDialogo(null);
    try {
      if (dialogo === 'aprovar') {
        const aprovacao = await relatoriosAula.aprovar(relatorioId, versao);
        await cliente.invalidateQueries({ queryKey: chaveRelatorioAula(relatorioId) });
        navegar(`/relatorios-aula/${relatorioId}/aprovado`, { replace: true, state: aprovacao });
      } else {
        await relatoriosAula.cancelar(relatorioId);
        navegar('/', { replace: true });
      }
    } catch (falha) {
      if (tratarConflito(falha)) {
        setDialogo(null);
      } else {
        setErroDialogo(falha);
      }
      setConfirmando(false);
    }
  };

  const mostrarTexto = !movel || aba === 'texto';
  const mostrarPdf = !movel || aba === 'pdf';

  return (
    <Estrutura
      navAtiva="alunos"
      contexto={contexto}
      barraAcoes={
        <>
          <Botao variante="link-perigo" disabled={aplicando} onClick={() => setDialogo('cancelar')}>
            {msg('CANCEL_REPORT_BUTTON')}
          </Botao>
          <Botao
            tamanho="grande"
            icone="verified"
            disabled={aplicando || desatualizada}
            onClick={() => setDialogo('aprovar')}
          >
            {msg('APPROVE_BUTTON')} · versão {versao}
          </Botao>
        </>
      }
    >
      <div className="pagina pagina--conferencia">
        <div>
          <div className="sobretitulo">Relatório de aula</div>
          <h1 className="titulo-pagina">{msg('REVIEW_TITLE')}</h1>
          <div className="conferencia__chips">
            <span className="chip chip--aviso">
              <Icone nome="schedule" tamanho="pequeno" />
              {msg('STATUS_AWAITING_REVIEW')}
            </span>
            <span className="chip chip--contorno">{msg('REVIEW_VERSION', { version: versao })}</span>
          </div>
        </div>
        <dl className="dados-relatorio">
          <div>
            <dt>ALUNO</dt>
            <dd>{dados.studentName}</dd>
          </div>
          <div>
            <dt>PROFESSOR</dt>
            <dd>{dados.teacherName}</dd>
          </div>
          <div>
            <dt>DATA DA AULA</dt>
            <dd>{formatarData(dados.lessonDate)}</dd>
          </div>
        </dl>
        {desatualizada ? (
          <div role="alert" className="alerta alerta--aviso">
            <Icone nome="sync_problem" />
            <div className="alerta__texto">
              <strong>Esta versão está desatualizada.</strong> O relatório foi alterado em outro lugar.
              Recarregue para conferir a versão atual antes de aprovar.
            </div>
            <Botao variante="aviso" tamanho="compacto" onClick={() => void recarregar()}>
              Recarregar
            </Botao>
          </div>
        ) : null}
        {aplicando ? (
          <div role="status" className="alerta alerta--info">
            <span className="girador" aria-hidden="true" />
            <div className="alerta__texto">
              <strong>{msg('CHANGE_BEING_APPLIED')}</strong> Uma nova versão será gerada em instantes.
            </div>
          </div>
        ) : null}
        {aviso ? (
          <div role="status" className={`alerta alerta--${aviso.tipo}`}>
            <Icone nome={aviso.tipo === 'erro' ? 'error' : aviso.tipo === 'ok' ? 'check_circle' : 'info'} />
            <div className="alerta__texto">
              {aviso.linhas.map((linha) => (
                <div key={linha}>{linha}</div>
              ))}
            </div>
          </div>
        ) : null}
        {movel ? (
          <div role="tablist" aria-label="Formato" className="abas">
            <button
              type="button"
              role="tab"
              id="aba-texto"
              aria-selected={aba === 'texto'}
              aria-controls="painel-texto"
              className="abas__aba"
              onClick={() => setAba('texto')}
            >
              Texto
            </button>
            <button
              type="button"
              role="tab"
              id="aba-pdf"
              aria-selected={aba === 'pdf'}
              aria-controls="painel-pdf"
              className="abas__aba"
              onClick={() => setAba('pdf')}
            >
              Ver PDF
            </button>
          </div>
        ) : null}
        <div className={`conferencia${movel ? '' : ' conferencia--lado-a-lado'}`}>
          {mostrarTexto ? (
            <div
              id="painel-texto"
              role={movel ? 'tabpanel' : undefined}
              aria-labelledby={movel ? 'aba-texto' : undefined}
              className="conferencia__texto"
            >
              <SecoesRelatorio conteudo={dados.content} esmaecido={aplicando} />
              <div className="campo">
                <label htmlFor={idAjuste} className="campo__rotulo">
                  {msg('CHANGE_REQUEST_LABEL')}
                </label>
                <textarea
                  id={idAjuste}
                  className="area-texto"
                  rows={3}
                  maxLength={4000}
                  value={ajuste}
                  disabled={aplicando || audio !== null}
                  placeholder="Ex.: o aluno teve mais facilidade em leitura do que consta nas dificuldades."
                  onChange={(evento) => setAjuste(evento.target.value)}
                />
              </div>
              <GravadorAudio
                key={chaveGravador}
                compacto
                limiteMb={inicio.data?.audioLimitMb ?? 50}
                aoMudar={setAudio}
                desabilitado={aplicando || enviandoAjuste}
              />
              {erroAjuste ? <AlertaErro erro={erroAjuste} /> : null}
              <Botao
                variante="secundario"
                icone="edit"
                className="conferencia__ajustar"
                disabled={!podeAjustar}
                carregando={enviandoAjuste}
                onClick={() => void solicitarAlteracao()}
              >
                {msg('REQUEST_CHANGE_BUTTON')}
              </Botao>
            </div>
          ) : null}
          {mostrarPdf ? (
            <div
              id="painel-pdf"
              role={movel ? 'tabpanel' : undefined}
              aria-labelledby={movel ? 'aba-pdf' : undefined}
            >
              <VisualizadorPdf
                caminho={caminhoPdfConferencia(dados.reportId, versao)}
                versao={versao}
                disponivel={Boolean(dados.pdfAvailable) && !aplicando}
                nomeArquivo={`relatorio-aula-${dados.lessonDate}-v${versao}.pdf`}
              />
            </div>
          ) : null}
        </div>
      </div>
      <DialogoConfirmacao
        aberto={dialogo === 'aprovar'}
        icone="verified"
        titulo={`Aprovar a versão ${versao}?`}
        texto={msg('CONFIRM_APPROVAL', { version: versao })}
        rotuloConfirmar={msg('CONFIRM_APPROVAL_BUTTON', { version: versao })}
        carregando={confirmando}
        erro={erroDialogo ? textoDoErro(erroDialogo) : undefined}
        aoConfirmar={() => void confirmar()}
        aoFechar={() => {
          setDialogo(null);
          setErroDialogo(null);
        }}
      />
      <DialogoConfirmacao
        aberto={dialogo === 'cancelar'}
        perigo
        icone="block"
        titulo="Cancelar este relatório?"
        texto={msg('CONFIRM_CANCELLATION')}
        rotuloConfirmar={msg('CONFIRM_CANCELLATION_BUTTON')}
        carregando={confirmando}
        erro={erroDialogo ? textoDoErro(erroDialogo) : undefined}
        aoConfirmar={() => void confirmar()}
        aoFechar={() => {
          setDialogo(null);
          setErroDialogo(null);
        }}
      />
    </Estrutura>
  );
}
