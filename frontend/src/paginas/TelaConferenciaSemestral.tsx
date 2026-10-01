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
import { SecoesSemestral } from '../componentes/SecoesSemestral';
import { VisualizadorPdf } from '../componentes/VisualizadorPdf';
import { msg } from '../mensagens/mensagens';
import { ErroApi } from '../servicos/clienteApi';
import { useInicio } from '../servicos/inicio';
import type { AudioSelecionado } from '../servicos/relatoriosAula';
import {
  caminhoPdfSemestral,
  chaveRelatorioSemestral,
  secoesSemestral,
  semestral,
  useRelatorioSemestral,
} from '../servicos/semestral';
import { usePollingTrabalho } from '../servicos/trabalhos';
import { avisoDoResultado } from './TelaConferencia';
import type { ResultadoNavegacao } from './TelaProcessando';

type Aviso = NonNullable<ReturnType<typeof avisoDoResultado>>;

/**
 * Tela 11 — Conferência semestral: os mesmos estados da 08 (PDF sendo gerado, aplicando alteração,
 * versão desatualizada), mais "N relatórios de aula foram considerados…", o aviso de relatórios em
 * aberto e as Informações Gerais somente leitura.
 */
export function TelaConferenciaSemestral() {
  const { relatorioId } = useParams();
  const navegar = useNavigate();
  const local = useLocation();
  const cliente = useQueryClient();
  const movel = useEhMovel();
  const relatorio = useRelatorioSemestral(relatorioId);
  const inicio = useInicio();
  const idAjuste = useId();
  const estadoInicial = local.state as ResultadoNavegacao | null;

  const [aba, setAba] = useState<'texto' | 'pdf'>('texto');
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
  const trabalhoEmCurso = trabalhoAlteracao ?? dados?.trabalhoEmCursoId ?? null;
  const polling = usePollingTrabalho(trabalhoEmCurso ?? undefined);
  const aviso =
    avisoAtual !== undefined
      ? avisoAtual
      : dados && estadoInicial?.resultado
        ? avisoDoResultado(estadoInicial.resultado, estadoInicial.textoEntendido, dados.versaoVigente)
        : null;

  useEffect(() => {
    if (!dados || trabalhoEmCurso) return;
    if (dados.status === 'APROVADO') {
      navegar(`/relatorios-semestrais/${dados.relatorioId}/aprovado`, { replace: true });
    } else if (dados.status === 'CANCELADO') {
      navegar('/', { replace: true });
    }
  }, [dados, trabalhoEmCurso, navegar]);

  const concluido = polling.finalizado ? polling.data : undefined;
  useEffect(() => {
    if (!concluido || !trabalhoEmCurso || !relatorioId) return;
    void (async () => {
      const atualizado = await cliente.fetchQuery({
        queryKey: chaveRelatorioSemestral(relatorioId),
        queryFn: () => semestral.obter(relatorioId),
        staleTime: 0,
      });
      setAviso(
        avisoDoResultado(
          concluido.resultadoCodigo ?? null,
          concluido.textoEntendido ?? null,
          atualizado.versaoVigente,
        ),
      );
      setTrabalhoAlteracao(null);
      setDesatualizada(false);
    })();
  }, [concluido, trabalhoEmCurso, cliente, relatorioId]);

  const contexto = {
    sobretitulo: 'Relatório semestral',
    titulo: dados?.informacoesGerais.nome ?? '',
    voltarPara: '/',
  };

  if (relatorio.isPending) {
    return (
      <Estrutura navAtiva="alunos" contexto={contexto}>
        <EstadoCarregando rotulo="Carregando o relatório semestral" />
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
  const versao = dados.versaoVigente;
  const podeAjustar = !aplicando && !enviandoAjuste && (ajuste.trim() !== '' || audio !== null);

  const tratarConflito = (falha: unknown): boolean => {
    if (falha instanceof ErroApi && falha.codigo === 'VERSAO_DESATUALIZADA') {
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
      const { trabalhoId } = audio
        ? await semestral.alterarAudio(relatorioId, audio, versao)
        : await semestral.alterarTexto(relatorioId, ajuste.trim(), versao);
      setTrabalhoAlteracao(trabalhoId);
      setAjuste('');
      setAudio(null);
      setChaveGravador((c) => c + 1);
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
        const aprovacao = await semestral.aprovar(relatorioId, versao);
        await cliente.invalidateQueries({ queryKey: chaveRelatorioSemestral(relatorioId) });
        navegar(`/relatorios-semestrais/${relatorioId}/aprovado`, { replace: true, state: aprovacao });
      } else {
        await semestral.cancelar(relatorioId);
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
            {msg('BOTAO_CANCELAR_RELATORIO')}
          </Botao>
          <Botao
            tamanho="grande"
            icone="verified"
            disabled={aplicando || desatualizada}
            onClick={() => setDialogo('aprovar')}
          >
            {msg('BOTAO_APROVAR')} · versão {versao}
          </Botao>
        </>
      }
    >
      <div className="pagina pagina--conferencia">
        <div>
          <div className="sobretitulo">Relatório semestral · {dados.informacoesGerais.periodoRotulo}</div>
          <h1 className="titulo-pagina">{msg('CONFERENCIA_TITULO')}</h1>
          <div className="conferencia__chips">
            <span className="chip chip--aviso">
              <Icone nome="schedule" tamanho="pequeno" />
              {msg('STATUS_AGUARDANDO_CONFERENCIA')}
            </span>
            <span className="chip chip--contorno">{msg('CONFERENCIA_VERSAO', { versao })}</span>
          </div>
        </div>
        <div className="consolidacao">
          <span className="consolidacao__numero">{dados.informacoesGerais.quantidadeAulasConsideradas}</span>{' '}
          <span>{msg('AULAS_CONSIDERADAS', { quantidade: '' }).trim()}</span>
        </div>
        {dados.quantidadeRelatoriosEmAberto > 0 ? (
          <div role="status" className="alerta alerta--aviso">
            <Icone nome="warning" />
            <div className="alerta__texto">
              {msg('HA_RELATORIOS_EM_ABERTO', { quantidade: dados.quantidadeRelatoriosEmAberto })}
            </div>
          </div>
        ) : null}
        {desatualizada ? (
          <div role="alert" className="alerta alerta--aviso">
            <Icone nome="sync_problem" />
            <div className="alerta__texto">
              <strong>Esta versão está desatualizada.</strong> O relatório foi alterado em outro lugar.
              Recarregue para conferir a versão atual antes de aprovar.
            </div>
            <Botao
              variante="aviso"
              tamanho="compacto"
              onClick={() => {
                setDesatualizada(false);
                void relatorio.refetch();
              }}
            >
              Recarregar
            </Botao>
          </div>
        ) : null}
        {aplicando ? (
          <div role="status" className="alerta alerta--info">
            <span className="girador" aria-hidden="true" />
            <div className="alerta__texto">
              <strong>{msg('ALTERACAO_EM_APLICACAO')}</strong> Uma nova versão será gerada em instantes.
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
              aria-selected={aba === 'texto'}
              className="abas__aba"
              onClick={() => setAba('texto')}
            >
              Texto
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={aba === 'pdf'}
              className="abas__aba"
              onClick={() => setAba('pdf')}
            >
              Ver PDF
            </button>
          </div>
        ) : null}
        <div className={`conferencia${movel ? '' : ' conferencia--lado-a-lado'}`}>
          {mostrarTexto ? (
            <div className="conferencia__texto">
              <SecoesSemestral
                secoes={secoesSemestral(dados.informacoesGerais, dados.conteudo)}
                esmaecido={aplicando}
              />
              <div className="campo">
                <label htmlFor={idAjuste} className="campo__rotulo">
                  {msg('PEDIDO_ALTERACAO_ROTULO')}
                </label>
                <textarea
                  id={idAjuste}
                  className="area-texto"
                  rows={3}
                  maxLength={4000}
                  value={ajuste}
                  disabled={aplicando || audio !== null}
                  placeholder="Ex.: incluir no parecer final a participação no recital de junho."
                  onChange={(evento) => setAjuste(evento.target.value)}
                />
              </div>
              <GravadorAudio
                key={chaveGravador}
                compacto
                limiteMb={inicio.data?.limiteAudioMb ?? 50}
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
                {msg('BOTAO_SOLICITAR_ALTERACAO')}
              </Botao>
            </div>
          ) : null}
          {mostrarPdf ? (
            <VisualizadorPdf
              caminho={caminhoPdfSemestral(dados.relatorioId, versao)}
              versao={versao}
              disponivel={Boolean(dados.pdfDisponivel) && !aplicando}
              nomeArquivo={`relatorio-semestral-${dados.periodoChave}-v${versao}.pdf`}
            />
          ) : null}
        </div>
      </div>
      <DialogoConfirmacao
        aberto={dialogo === 'aprovar'}
        icone="verified"
        titulo={`Aprovar a versão ${versao}?`}
        texto={msg('CONFIRMAR_APROVACAO', { versao })}
        rotuloConfirmar={msg('BOTAO_CONFIRMAR_APROVACAO', { versao })}
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
        texto={msg('CONFIRMAR_CANCELAMENTO')}
        rotuloConfirmar={msg('BOTAO_CONFIRMAR_CANCELAMENTO')}
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
