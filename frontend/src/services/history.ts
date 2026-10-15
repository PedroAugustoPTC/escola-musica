import { useQuery } from '@tanstack/react-query';
import { msg } from '../messages/messages';
import { api, type Schemas } from './apiClient';
import { instantDate, instantTime } from './formatting';

export type HistoryDto = Schemas['HistoryDto'];
export type HistoryItemDto = Schemas['HistoryItemDto'];

const MONTHS = ['JAN', 'FEV', 'MAR', 'ABR', 'MAI', 'JUN', 'JUL', 'AGO', 'SET', 'OUT', 'NOV', 'DEZ'];

export function useHistory(studentId: string | undefined) {
  return useQuery<HistoryDto>({
    queryKey: ['history', studentId],
    queryFn: ({ signal }) => api.get<HistoryDto>(`/students/${studentId}/history`, signal),
    enabled: Boolean(studentId),
  });
}

/** `APROVADO_EM` com data e hora no fuso da escola (o mesmo texto do bot). */
export function approvedAtText(item: HistoryItemDto): string {
  return msg('APPROVED_AT', { data: instantDate(item.approvedAt), time: instantTime(item.approvedAt) });
}

/** Selo de data do item: dia e mês da aula, ou o semestre ("1º"/"2º" e "SEM"). */
export function itemSeal(item: HistoryItemDto): { day: string; month: string } {
  if (item.type === 'SEMESTER') {
    const startMonth = Number((item.periodStart ?? '').slice(5, 7));
    return { day: startMonth >= 7 ? '2º' : '1º', month: 'SEM' };
  }
  const data = item.lessonDate ?? '';
  return { day: data.slice(8, 10), month: MONTHS[Number(data.slice(5, 7)) - 1] ?? '' };
}

/** Caminho do PDF oficial e o nome sugerido para download. */
export function pdfOfficial(item: HistoryItemDto): { path: string; fileName: string } {
  if (item.type === 'SEMESTER') {
    const start = item.periodStart ?? '';
    const semester = Number(start.slice(5, 7)) >= 7 ? 2 : 1;
    return {
      path: `/semester-reports/${item.reportId}/official-pdf`,
      fileName: `relatorio-semestral-${start.slice(0, 4)}-${semester}.pdf`,
    };
  }
  return {
    path: `/lesson-reports/${item.reportId}/official-pdf`,
    fileName: `relatorio-aula-${item.lessonDate ?? ''}.pdf`,
  };
}
