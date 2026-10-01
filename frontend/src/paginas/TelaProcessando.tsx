import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { Botao } from '../componentes/Botao';
import { EstadoCarregando } from '../componentes/EstadoCarregando';
import { AlertaErro, EstadoErro } from '../componentes/EstadoErro';
import { Estrutura } from '../componentes/Estrutura';
import { EtapasProcessamento } from '../componentes/EtapasProcessamento';
import { Icone } from '../componentes/Icone';
import { msg } from '../mensagens/mensagens';
import { relatoriosAula } from '../servicos/relatoriosAula';
import { usePollingTrabalho, type TrabalhoDto } from '../servicos/trabalhos';

/** Estado passado à próxima tela para mostrar o resultado (texto entendido, código). */
export interface ResultadoNavegacao {
  resultado: string | null;
  textoEntendido: string | null;
}

/** Para onde levar o professor quando o trabalho termina com sucesso. */
export function destinoDoResultado(trabalho: TrabalhoDto): string | null {
  if (!trabalho.relatorioId) return null;
  const base =
    trabalho.relatorioTipo === 'SEMESTRAL'
      ? `/relatorios-semestrais/${trabalho.relatorioId}`
      : `/relatorios-aula/${trabalho.relatorioId}`;
  switch (trabalho.resultadoCodigo) {
    case 'PERGUNTAS_PENDENTES':
    case 'FALHA_RESPOSTA':
      return `${base}/perguntas`;
    case 'FALHA_ESTRUTURACAO':
    case 'FALHA_CONSOLIDACAO':
    case 'AUDIO_NAO_PROCESSAVEL':
      return null;
    default:
      return base;
  }
}

function tituloEtapa(trabalho: TrabalhoDto): string {
  const etapa = msg(`ETAPA_${trabalho.etapa}`);
  return `${etapa}…`;
}

/** Tela 06 — Processando (06b enviando, 06c falha, aviso de demora após 3 min). */
export function TelaProcessando() {
  const { trabalhoId } = useParams();
  const navegar = useNavigate();
  const local = useLocation();
  const alunoId = (local.state as { alunoId?: string } | null)?.alunoId;
  const trabalho = usePollingTrabalho(trabalhoId);
  const [acao, setAcao] = useState<'tentar' | 'cancelar' | null>(null);
  const [erroAcao, setErroAcao] = useState<unknown>(null);

  const dados = trabalho.data;

  useEffect(() => {
    if (!dados || !trabalho.finalizado) return;
    if (dados.status === 'DESCARTADO') {
      navegar('/', { replace: true });
      return;
    }
    const destino = destinoDoResultado(dados);
    if (destino) {
      const estado: ResultadoNavegacao = {
        resultado: dados.resultadoCodigo ?? null,
        textoEntendido: dados.textoEntendido ?? null,
      };
      navegar(destino, { replace: true, state: estado });
    }
  }, [dados, trabalho.finalizado, navegar]);

  const contexto = { sobretitulo: 'Relatório de aula', titulo: 'Processando', voltarPara: '/' };

  if (trabalho.isPending) {
    return (
      <Estrutura contexto={contexto}>
        <EstadoCarregando rotulo="Consultando o processamento" />
      </Estrutura>
    );
  }
  if (trabalho.isError) {
    return (
      <Estrutura contexto={contexto}>
        <EstadoErro erro={trabalho.error} aoTentarDeNovo={() => void trabalho.refetch()} />
      </Estrutura>
    );
  }
  if (!dados) return null;

  const falhou =
    trabalho.finalizado &&
    (dados.resultadoCodigo === 'FALHA_ESTRUTURACAO' || dados.resultadoCodigo === 'AUDIO_NAO_PROCESSAVEL');

  const tentarNovamente = async () => {
    if (!dados.relatorioId) return;
    setAcao('tentar');
    setErroAcao(null);
    try {
      const { trabalhoId: novo } = await relatoriosAula.novaTentativa(dados.relatorioId);
      navegar(`/trabalhos/${novo}`, { replace: true, state: { alunoId } });
    } catch (falha) {
      setErroAcao(falha);
    } finally {
      setAcao(null);
    }
  };

  const cancelar = async () => {
    if (!dados.relatorioId) return;
    setAcao('cancelar');
    setErroAcao(null);
    try {
      await relatoriosAula.cancelar(dados.relatorioId);
      navegar('/', { replace: true });
    } catch (falha) {
      setErroAcao(falha);
      setAcao(null);
    }
  };

  if (falhou) {
    const semRelatorio = dados.resultadoCodigo === 'AUDIO_NAO_PROCESSAVEL';
    return (
      <Estrutura contexto={contexto}>
        <div className="pagina pagina--centro">
          <div role="alert" className="cartao cartao--falha">
            <span className="circulo-icone circulo-icone--erro">
              <Icone nome="error" />
            </span>
            <div>
              <h1 className="titulo-pagina titulo-pagina--menor">
                {semRelatorio ? 'Não foi possível entender o áudio.' : 'Não foi possível processar o áudio.'}
              </h1>
              <p className="subtitulo">
                {semRelatorio ? msg('AUDIO_NAO_PROCESSAVEL') : msg('FALHA_PROCESSAMENTO_OPCOES')}
              </p>
            </div>
            {erroAcao ? <AlertaErro erro={erroAcao} /> : null}
            <div className="pilha-acoes">
              {semRelatorio ? (
                <Botao
                  tamanho="grande"
                  icone="mic"
                  onClick={() => navegar(alunoId ? `/alunos/${alunoId}/nova-aula` : '/alunos?registrar=1')}
                >
                  Gravar novamente
                </Botao>
              ) : (
                <>
                  <Botao
                    tamanho="grande"
                    icone="refresh"
                    carregando={acao === 'tentar'}
                    disabled={acao !== null}
                    onClick={() => void tentarNovamente()}
                  >
                    {msg('BOTAO_TENTAR_NOVAMENTE')}
                  </Botao>
                  <Botao
                    variante="link-perigo"
                    carregando={acao === 'cancelar'}
                    disabled={acao !== null}
                    onClick={() => void cancelar()}
                  >
                    {msg('BOTAO_CANCELAR_RELATORIO')}
                  </Botao>
                </>
              )}
            </div>
          </div>
        </div>
      </Estrutura>
    );
  }

  return (
    <Estrutura contexto={contexto}>
      <div className="pagina pagina--centro">
        <div aria-live="polite">
          <div className="sobretitulo">
            {dados.tipo === 'REGISTRO_AULA' ? 'Novo relatório de aula' : 'Relatório de aula'}
          </div>
          <h1 className="titulo-pagina">{tituloEtapa(dados)}</h1>
          <p className="subtitulo">
            Isso pode levar alguns segundos. Mantenha esta tela aberta — avisaremos assim que o relatório
            estiver pronto.
          </p>
        </div>
        <EtapasProcessamento trabalho={dados} />
        {dados.demorando || trabalho.excedeuLimite ? (
          <div role="status" className="alerta alerta--aviso">
            <Icone nome="hourglass_top" />
            <div className="alerta__texto">{msg('PROCESSAMENTO_DEMORADO')}</div>
            <Botao variante="aviso" tamanho="compacto" onClick={() => navegar('/')}>
              Voltar ao Painel
            </Botao>
          </div>
        ) : null}
      </div>
    </Estrutura>
  );
}
