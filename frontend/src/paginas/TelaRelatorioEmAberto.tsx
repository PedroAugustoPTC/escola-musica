import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Navigate, useNavigate, useParams } from 'react-router';
import { Botao } from '../componentes/Botao';
import { DialogoConfirmacao } from '../componentes/DialogoConfirmacao';
import { EstadoCarregando } from '../componentes/EstadoCarregando';
import { EstadoErro, textoDoErro } from '../componentes/EstadoErro';
import { Estrutura } from '../componentes/Estrutura';
import { Icone } from '../componentes/Icone';
import { msg } from '../mensagens/mensagens';
import { api } from '../servicos/clienteApi';
import { dataDoInstante, horaDoInstante } from '../servicos/formatacao';
import type { RelatorioEmAbertoDto } from '../servicos/inicio';
import { relatoriosAula } from '../servicos/relatoriosAula';

/** Para onde "Continuar de onde parei" leva, conforme o status e a etapa (US3). */
export function rotaDeContinuacao(aberto: RelatorioEmAbertoDto): string {
  if (!aberto.reportId) {
    return aberto.jobId ? `/trabalhos/${aberto.jobId}` : '/';
  }
  if (aberto.type === 'SEMESTER') return `/relatorios-semestrais/${aberto.reportId}`;
  if (aberto.status === 'AWAITING_ANSWERS') return `/relatorios-aula/${aberto.reportId}/perguntas`;
  return `/relatorios-aula/${aberto.reportId}`;
}

/** Tela 04 — Relatório em aberto (04b confirmação de descarte), FR-034. */
export function TelaRelatorioEmAberto() {
  const { alunoId } = useParams();
  const navegar = useNavigate();
  const [confirmar, setConfirmar] = useState(false);
  const [descartando, setDescartando] = useState(false);
  const [erro, setErro] = useState<unknown>(null);
  const consulta = useQuery<RelatorioEmAbertoDto | null>({
    queryKey: ['relatorio-em-aberto', alunoId],
    queryFn: async ({ signal }) =>
      (await api.get<RelatorioEmAbertoDto | undefined>(`/students/${alunoId}/open-report`, signal)) ??
      null,
    gcTime: 0,
    enabled: Boolean(alunoId),
  });
  const contexto = { sobretitulo: 'Relatório de aula', titulo: 'Em aberto', voltarPara: '/alunos' };

  if (consulta.isPending) {
    return (
      <Estrutura navAtiva="alunos" contexto={contexto}>
        <EstadoCarregando rotulo="Consultando o relatório em aberto" />
      </Estrutura>
    );
  }
  if (consulta.isError) {
    return (
      <Estrutura navAtiva="alunos" contexto={contexto}>
        <EstadoErro erro={consulta.error} aoTentarDeNovo={() => void consulta.refetch()} />
      </Estrutura>
    );
  }
  const aberto = consulta.data;
  if (!aberto) {
    // nada em aberto (204): segue direto para a gravação
    return <Navigate to={`/alunos/${alunoId}/nova-aula`} replace />;
  }

  const canal = msg(aberto.originChannel === 'TELEGRAM' ? 'TELEGRAM_ORIGIN_CHANNEL' : 'WEB_ORIGIN_CHANNEL');
  const quando = `${dataDoInstante(aberto.updatedAt)} às ${horaDoInstante(aberto.updatedAt)}`;

  const descartar = async () => {
    setDescartando(true);
    setErro(null);
    try {
      if (aberto.reportId) {
        await relatoriosAula.cancelar(aberto.reportId);
      } else if (aberto.jobId) {
        await api.post<void>(`/jobs/${aberto.jobId}/discard`);
      }
      navegar(`/alunos/${alunoId}/nova-aula`, { replace: true });
    } catch (falha) {
      setErro(falha);
      setDescartando(false);
    }
  };

  return (
    <Estrutura navAtiva="alunos" contexto={contexto}>
      <div className="pagina pagina--estreita">
        <div className="cartao cartao--retomada">
          <span className="circulo-icone circulo-icone--aviso">
            <Icone nome="edit_note" />
          </span>
          <div>
            <div className="sobretitulo">{aberto.studentName}</div>
            <h1 className="titulo-pagina titulo-pagina--menor">Você tem um relatório em aberto</h1>
            <p className="subtitulo">
              Este aluno já tem um relatório iniciado {canal} e ainda não concluído (última alteração em{' '}
              {quando}).
            </p>
          </div>
          <div className="pilha-acoes">
            <Botao tamanho="grande" onClick={() => navegar(rotaDeContinuacao(aberto))}>
              {msg('CONTINUE_BUTTON')}
            </Botao>
            <Botao variante="perigo" tamanho="grande" onClick={() => setConfirmar(true)}>
              {msg('DISCARD_BUTTON')}
            </Botao>
          </div>
        </div>
      </div>
      <DialogoConfirmacao
        aberto={confirmar}
        perigo
        icone="delete"
        titulo="Descartar o relatório em aberto?"
        texto={msg('CONFIRM_DISCARD')}
        rotuloConfirmar={msg('CONFIRM_DISCARD_BUTTON')}
        carregando={descartando}
        erro={erro ? textoDoErro(erro) : undefined}
        aoConfirmar={() => void descartar()}
        aoFechar={() => {
          setConfirmar(false);
          setErro(null);
        }}
      />
    </Estrutura>
  );
}
