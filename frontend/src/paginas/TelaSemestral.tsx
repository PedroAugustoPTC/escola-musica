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
  return msg('SEMESTER_INTERVAL', {
    label: semestre.label,
    start: formatarData(semestre.start),
    end: formatarData(semestre.end),
  });
}

function textoEmAberto(aberto: RelatorioEmAbertoDto): string {
  return msg('REPORT_ALREADY_OPEN', {
    originChannel: msg(aberto.originChannel === 'TELEGRAM' ? 'TELEGRAM_ORIGIN_CHANNEL' : 'WEB_ORIGIN_CHANNEL'),
    data: dataDoInstante(aberto.updatedAt),
    hora: horaDoInstante(aberto.updatedAt),
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
  const chave = escolhido ?? semestres.data?.find((s) => s.preselected)?.key ?? null;
  const previa = usePreviaSemestral(alunoId, chave);
  const [gerando, setGerando] = useState(false);
  const [erro, setErro] = useState<unknown>(null);
  const [descartar, setDescartar] = useState(false);
  const [descartando, setDescartando] = useState(false);
  const [erroDescarte, setErroDescarte] = useState<unknown>(null);

  const contexto = {
    sobretitulo: 'Relatório semestral',
    titulo: aluno.data?.name ?? '',
    voltarPara: '/alunos',
  };

  if (aluno.isPending || semestres.isPending) {
    return (
      <Estrutura navAtiva="alunos" contexto={contexto}>
        <EstadoCarregando rotulo="Carregando os semestres" />
      </Estrutura>
    );
  }
  if (aluno.isError || semestres.isError) {
    return (
      <Estrutura navAtiva="alunos" contexto={contexto}>
        <EstadoErro erro={aluno.error ?? semestres.error} aoTentarDeNovo={() => void semestres.refetch()} />
      </Estrutura>
    );
  }

  const lista = semestres.data;
  const selecionado = lista.find((s) => s.key === chave) ?? null;
  const dados = previa.data;
  const podeGerar =
    dados !== undefined &&
    !dados.approvedSemesterReportId &&
    !dados.openSemesterReport &&
    dados.approvedLessons > 0 &&
    dados.openLessonReports === 0;

  const gerar = async (mesmoComEmAberto: boolean) => {
    if (!alunoId || !chave) return;
    setGerando(true);
    setErro(null);
    try {
      const { jobId } = await semestral.gerar(alunoId, chave, mesmoComEmAberto);
      navegar(`/trabalhos/${jobId}`);
    } catch (falha) {
      setErro(falha);
      setGerando(false);
    }
  };

  const confirmarDescarte = async () => {
    const aberto = dados?.openSemesterReport;
    if (!aberto) return;
    setDescartando(true);
    setErroDescarte(null);
    try {
      if (aberto.reportId) {
        await semestral.cancelar(aberto.reportId);
      } else if (aberto.jobId) {
        await semestral.descartarConsolidacao(aberto.jobId);
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
      navAtiva="alunos"
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
            {msg('GENERATE_SEMESTER_BUTTON')}
          </Botao>
        ) : undefined
      }
    >
      <div className="pagina pagina--estreita">
        <div>
          <div className="sobretitulo">Relatório semestral</div>
          <h1 className="titulo-pagina">{aluno.data.name}</h1>
          <p className="subtitulo">
            Escolha o período. O relatório consolida todas as aulas aprovadas no semestre.
          </p>
        </div>
        {lista.length === 0 ? (
          <EstadoVazio icone="event_busy" texto={msg('NO_SEMESTERS')} />
        ) : (
          <>
            <fieldset className="periodos">
              <legend className="rotulo-secao">Período</legend>
              <div role="radiogroup" aria-label="Período" className="periodos__opcoes">
                {lista.map((semestre) => (
                  <button
                    key={semestre.key}
                    type="button"
                    role="radio"
                    aria-checked={semestre.key === chave}
                    className="periodos__opcao"
                    onClick={() => {
                      setEscolhido(semestre.key);
                      setErro(null);
                    }}
                  >
                    <Icone nome={semestre.key === chave ? 'check_circle' : 'calendar_month'} />
                    {semestre.label}
                    {semestre.inProgress ? (
                      <span className="chip chip--contorno">{msg('SEMESTER_IN_PROGRESS')}</span>
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
            ) : dados.approvedSemesterReportId ? (
              <div role="status" className="alerta alerta--info">
                <Icone nome="verified" />
                <div className="alerta__texto">{msg('SEMESTER_REPORT_ALREADY_APPROVED')}</div>
                <Link className="botao botao--link" to={`/alunos/${alunoId}/historico`}>
                  {msg('HISTORY_BUTTON')}
                </Link>
              </div>
            ) : dados.openSemesterReport ? (
              <div role="alert" className="alerta alerta--aviso alerta--coluna">
                <Icone nome="edit_note" />
                <div className="alerta__texto">{textoEmAberto(dados.openSemesterReport)}</div>
                <div className="pilha-acoes">
                  <Botao
                    variante="aviso"
                    onClick={() =>
                      navegar(
                        dados.openSemesterReport?.reportId
                          ? `/relatorios-semestrais/${dados.openSemesterReport.reportId}`
                          : `/trabalhos/${dados.openSemesterReport?.jobId}`,
                      )
                    }
                  >
                    {msg('CONTINUE_BUTTON')}
                  </Botao>
                  <Botao variante="perigo" onClick={() => setDescartar(true)}>
                    {msg('DISCARD_BUTTON')}
                  </Botao>
                </div>
              </div>
            ) : dados.approvedLessons === 0 ? (
              <div role="alert" className="alerta alerta--erro">
                <Icone nome="error" />
                <div className="alerta__texto">{msg('NO_APPROVED_LESSONS_IN_PERIOD')}</div>
              </div>
            ) : (
              <>
                <div role="status" className="alerta alerta--info">
                  <Icone nome="info" />
                  <div className="alerta__texto">
                    {msg('SEMESTER_PREVIEW', {
                      label: selecionado?.label ?? '',
                      quantity: dados.approvedLessons,
                    })}
                  </div>
                </div>
                {dados.openLessonReports > 0 ? (
                  <div role="alert" className="alerta alerta--aviso alerta--coluna">
                    <Icone nome="warning" />
                    <div className="alerta__texto">
                      {msg('OPEN_REPORTS_EXIST', { quantity: dados.openLessonReports })}
                    </div>
                    <div className="pilha-acoes">
                      <Botao variante="aviso" carregando={gerando} onClick={() => void gerar(true)}>
                        {msg('GENERATE_ANYWAY_BUTTON')}
                      </Botao>
                      <Botao variante="secundario" onClick={() => navegar('/alunos')}>
                        {msg('BACK_BUTTON')}
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
        texto={msg('CONFIRM_DISCARD')}
        rotuloConfirmar={msg('CONFIRM_DISCARD_BUTTON')}
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
