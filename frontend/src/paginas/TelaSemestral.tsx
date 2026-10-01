import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { Botao } from '../componentes/Botao';
import { DialogoConfirmacao } from '../componentes/DialogoConfirmacao';
import { EstadoCarregando } from '../componentes/EstadoCarregando';
import { AlertaErro, EstadoErro, textoDoErro } from '../componentes/EstadoErro';
import { EstadoVazio } from '../componentes/EstadoVazio';
import { Estrutura } from '../componentes/Estrutura';
import { Icone } from '../componentes/Icone';
import { msg } from '../mensagens/mensagens';
import { useAluno } from '../servicos/alunos';
import { dataDoInstante, formatarData, horaDoInstante } from '../servicos/formatacao';
import type { RelatorioEmAbertoDto } from '../servicos/inicio';
import { semestral, usePreviaSemestral, useSemestres, type SemestreDto } from '../servicos/semestral';

function intervalo(semestre: SemestreDto): string {
  return msg('SEMESTRE_INTERVALO', {
    rotulo: semestre.rotulo,
    inicio: formatarData(semestre.inicio),
    fim: formatarData(semestre.fim),
  });
}

function textoEmAberto(aberto: RelatorioEmAbertoDto): string {
  return msg('RELATORIO_EM_ABERTO', {
    canalOrigem: msg(aberto.canalOrigem === 'TELEGRAM' ? 'CANAL_ORIGEM_TELEGRAM' : 'CANAL_ORIGEM_WEB'),
    data: dataDoInstante(aberto.atualizadoEm),
    hora: horaDoInstante(aberto.atualizadoEm),
  });
}

/**
 * Tela 10 — Semestral: período (o mais recente pré-selecionado) e prévia (10b sem aulas aprovadas,
 * 10c nenhum semestre, aviso de relatórios de aula em aberto, já aprovado, semestral em aberto).
 */
export function TelaSemestral() {
  const { alunoId } = useParams();
  const navegar = useNavigate();
  const aluno = useAluno(alunoId);
  const semestres = useSemestres(alunoId);
  const [escolhido, setEscolhido] = useState<string | null>(null);
  const chave = escolhido ?? semestres.data?.find((s) => s.preSelecionado)?.chave ?? null;
  const previa = usePreviaSemestral(alunoId, chave);
  const [gerando, setGerando] = useState(false);
  const [erro, setErro] = useState<unknown>(null);
  const [descartar, setDescartar] = useState(false);
  const [descartando, setDescartando] = useState(false);
  const [erroDescarte, setErroDescarte] = useState<unknown>(null);

  const contexto = {
    sobretitulo: 'Relatório semestral',
    titulo: aluno.data?.nome ?? '',
    voltarPara: '/alunos',
  };

  if (aluno.isPending || semestres.isPending) {
    return (
      <Estrutura contexto={contexto}>
        <EstadoCarregando rotulo="Carregando os semestres" />
      </Estrutura>
    );
  }
  if (aluno.isError || semestres.isError) {
    return (
      <Estrutura contexto={contexto}>
        <EstadoErro erro={aluno.error ?? semestres.error} aoTentarDeNovo={() => void semestres.refetch()} />
      </Estrutura>
    );
  }

  const lista = semestres.data;
  const selecionado = lista.find((s) => s.chave === chave) ?? null;
  const dados = previa.data;
  const podeGerar =
    dados !== undefined &&
    !dados.semestralAprovadoId &&
    !dados.semestralEmAberto &&
    dados.aulasAprovadas > 0 &&
    dados.relatoriosAulaEmAberto === 0;

  const gerar = async (mesmoComEmAberto: boolean) => {
    if (!alunoId || !chave) return;
    setGerando(true);
    setErro(null);
    try {
      const { trabalhoId } = await semestral.gerar(alunoId, chave, mesmoComEmAberto);
      navegar(`/trabalhos/${trabalhoId}`);
    } catch (falha) {
      setErro(falha);
      setGerando(false);
    }
  };

  const confirmarDescarte = async () => {
    const aberto = dados?.semestralEmAberto;
    if (!aberto) return;
    setDescartando(true);
    setErroDescarte(null);
    try {
      if (aberto.relatorioId) {
        await semestral.cancelar(aberto.relatorioId);
      } else if (aberto.trabalhoId) {
        await semestral.descartarConsolidacao(aberto.trabalhoId);
      }
      setDescartar(false);
      await previa.refetch();
    } catch (falha) {
      setErroDescarte(falha);
    } finally {
      setDescartando(false);
    }
  };

  return (
    <Estrutura
      contexto={contexto}
      barraEstreita
      barraAcoes={
        lista.length > 0 ? (
          <Botao
            tamanho="grande"
            bloco
            icone="description"
            disabled={!podeGerar}
            carregando={gerando}
            onClick={() => void gerar(false)}
          >
            {msg('BOTAO_GERAR_SEMESTRAL')}
          </Botao>
        ) : undefined
      }
    >
      <div className="pagina pagina--estreita">
        <div>
          <div className="sobretitulo">Relatório semestral</div>
          <h1 className="titulo-pagina">{aluno.data.nome}</h1>
          <p className="subtitulo">
            Escolha o período. O relatório consolida todas as aulas aprovadas no semestre.
          </p>
        </div>
        {lista.length === 0 ? (
          <EstadoVazio icone="event_busy" texto={msg('SEM_SEMESTRES')} />
        ) : (
          <>
            <fieldset className="periodos">
              <legend className="rotulo-secao">Período</legend>
              <div role="radiogroup" aria-label="Período" className="periodos__opcoes">
                {lista.map((semestre) => (
                  <button
                    key={semestre.chave}
                    type="button"
                    role="radio"
                    aria-checked={semestre.chave === chave}
                    className="periodos__opcao"
                    onClick={() => {
                      setEscolhido(semestre.chave);
                      setErro(null);
                    }}
                  >
                    <Icone nome={semestre.chave === chave ? 'check_circle' : 'calendar_month'} />
                    {semestre.rotulo}
                    {semestre.emAndamento ? (
                      <span className="chip chip--contorno">{msg('SEMESTRE_EM_ANDAMENTO')}</span>
                    ) : null}
                  </button>
                ))}
              </div>
              {selecionado ? <div className="campo__ajuda">{intervalo(selecionado)}</div> : null}
            </fieldset>
            {previa.isError ? (
              <AlertaErro erro={previa.error} aoTentarDeNovo={() => void previa.refetch()} />
            ) : !dados ? (
              <EstadoCarregando rotulo="Consultando o período" />
            ) : dados.semestralAprovadoId ? (
              <div role="status" className="alerta alerta--info">
                <Icone nome="verified" />
                <div className="alerta__texto">{msg('SEMESTRAL_JA_APROVADO')}</div>
                <Link className="botao botao--link" to={`/alunos/${alunoId}/historico`}>
                  {msg('BOTAO_HISTORICO')}
                </Link>
              </div>
            ) : dados.semestralEmAberto ? (
              <div role="alert" className="alerta alerta--aviso alerta--coluna">
                <Icone nome="edit_note" />
                <div className="alerta__texto">{textoEmAberto(dados.semestralEmAberto)}</div>
                <div className="pilha-acoes">
                  <Botao
                    variante="aviso"
                    onClick={() =>
                      navegar(
                        dados.semestralEmAberto?.relatorioId
                          ? `/relatorios-semestrais/${dados.semestralEmAberto.relatorioId}`
                          : `/trabalhos/${dados.semestralEmAberto?.trabalhoId}`,
                      )
                    }
                  >
                    {msg('BOTAO_CONTINUAR')}
                  </Botao>
                  <Botao variante="perigo" onClick={() => setDescartar(true)}>
                    {msg('BOTAO_DESCARTAR')}
                  </Botao>
                </div>
              </div>
            ) : dados.aulasAprovadas === 0 ? (
              <div role="alert" className="alerta alerta--erro">
                <Icone nome="error" />
                <div className="alerta__texto">{msg('SEM_AULAS_APROVADAS_NO_PERIODO')}</div>
              </div>
            ) : (
              <>
                <div role="status" className="alerta alerta--info">
                  <Icone nome="info" />
                  <div className="alerta__texto">
                    {msg('SEMESTRAL_PREVIA', {
                      rotulo: selecionado?.rotulo ?? '',
                      quantidade: dados.aulasAprovadas,
                    })}
                  </div>
                </div>
                {dados.relatoriosAulaEmAberto > 0 ? (
                  <div role="alert" className="alerta alerta--aviso alerta--coluna">
                    <Icone nome="warning" />
                    <div className="alerta__texto">
                      {msg('HA_RELATORIOS_EM_ABERTO', { quantidade: dados.relatoriosAulaEmAberto })}
                    </div>
                    <div className="pilha-acoes">
                      <Botao variante="aviso" carregando={gerando} onClick={() => void gerar(true)}>
                        {msg('BOTAO_GERAR_MESMO_ASSIM')}
                      </Botao>
                      <Botao variante="secundario" onClick={() => navegar('/alunos')}>
                        {msg('BOTAO_VOLTAR')}
                      </Botao>
                    </div>
                  </div>
                ) : null}
              </>
            )}
            {erro ? <AlertaErro erro={erro} /> : null}
          </>
        )}
      </div>
      <DialogoConfirmacao
        aberto={descartar}
        perigo
        icone="delete"
        titulo="Descartar o relatório em aberto?"
        texto={msg('CONFIRMAR_DESCARTE')}
        rotuloConfirmar={msg('BOTAO_CONFIRMAR_DESCARTE')}
        carregando={descartando}
        erro={erroDescarte ? textoDoErro(erroDescarte) : undefined}
        aoConfirmar={() => void confirmarDescarte()}
        aoFechar={() => {
          setDescartar(false);
          setErroDescarte(null);
        }}
      />
    </Estrutura>
  );
}
