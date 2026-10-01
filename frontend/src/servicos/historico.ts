import { useQuery } from '@tanstack/react-query';
import { msg } from '../mensagens/mensagens';
import { api, type Esquemas } from './clienteApi';
import { dataDoInstante, horaDoInstante } from './formatacao';

export type HistoricoDto = Esquemas['HistoricoDto'];
export type ItemHistoricoDto = Esquemas['ItemHistoricoDto'];

const MESES = ['JAN', 'FEV', 'MAR', 'ABR', 'MAI', 'JUN', 'JUL', 'AGO', 'SET', 'OUT', 'NOV', 'DEZ'];

export function useHistorico(alunoId: string | undefined) {
  return useQuery<HistoricoDto>({
    queryKey: ['historico', alunoId],
    queryFn: ({ signal }) => api.get<HistoricoDto>(`/alunos/${alunoId}/historico`, signal),
    enabled: Boolean(alunoId),
  });
}

/** `APROVADO_EM` com data e hora no fuso da escola (o mesmo texto do bot). */
export function textoAprovadoEm(item: ItemHistoricoDto): string {
  return msg('APROVADO_EM', { data: dataDoInstante(item.aprovadoEm), hora: horaDoInstante(item.aprovadoEm) });
}

/** Selo de data do item: dia e mês da aula, ou o semestre ("1º"/"2º" e "SEM"). */
export function seloDoItem(item: ItemHistoricoDto): { dia: string; mes: string } {
  if (item.tipo === 'SEMESTRAL') {
    const mesInicio = Number((item.periodoInicio ?? '').slice(5, 7));
    return { dia: mesInicio >= 7 ? '2º' : '1º', mes: 'SEM' };
  }
  const data = item.dataAula ?? '';
  return { dia: data.slice(8, 10), mes: MESES[Number(data.slice(5, 7)) - 1] ?? '' };
}

/** Caminho do PDF oficial e o nome sugerido para download. */
export function pdfOficial(item: ItemHistoricoDto): { caminho: string; nomeArquivo: string } {
  if (item.tipo === 'SEMESTRAL') {
    const inicio = item.periodoInicio ?? '';
    const semestre = Number(inicio.slice(5, 7)) >= 7 ? 2 : 1;
    return {
      caminho: `/relatorios-semestrais/${item.relatorioId}/pdf-oficial`,
      nomeArquivo: `relatorio-semestral-${inicio.slice(0, 4)}-${semestre}.pdf`,
    };
  }
  return {
    caminho: `/relatorios-aula/${item.relatorioId}/pdf-oficial`,
    nomeArquivo: `relatorio-aula-${item.dataAula ?? ''}.pdf`,
  };
}
