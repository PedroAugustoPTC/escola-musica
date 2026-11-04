import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '../apiClient';
import type { components } from '../school-history-api-types';
import { ADMIN_QUERY_ROOT } from './session';
import { DEFAULT_PAGE_SIZE, queryString } from './query';
import type { TeacherPage } from './teachers';

type Schemas = components['schemas'];

export type SchoolHistoryPage = Schemas['SchoolHistoryPage'];
export type SchoolHistoryItem = Schemas['SchoolHistoryItem'];
export type PersonSummary = Schemas['PersonSummary'];
export type SchoolReportType = SchoolHistoryItem['type'];

/** Filtros da lista: só identificadores, tipo, datas e página (FR-015); nunca nome nem CPF. */
export interface SchoolHistoryFilters {
  studentId?: string;
  teacherId?: string;
  type?: SchoolReportType;
  /** `AAAA-MM-DD`, inclusive. */
  from?: string;
  /** `AAAA-MM-DD`, inclusive. */
  to?: string;
  page: number;
}

const HISTORY = [ADMIN_QUERY_ROOT, 'school-history'] as const;

/** Chave de consulta só com identificadores, tipo, datas e página. */
export function schoolHistoryKey(filters: SchoolHistoryFilters) {
  return [
    ...HISTORY,
    'list',
    {
      studentId: filters.studentId ?? null,
      teacherId: filters.teacherId ?? null,
      type: filters.type ?? null,
      from: filters.from ?? null,
      to: filters.to ?? null,
      page: filters.page,
    },
  ] as const;
}

/** Caminho da consulta (relativo a `/api/v1`), com os filtros presentes. */
export function schoolHistoryPath(filters: SchoolHistoryFilters): string {
  return `/admin/school-history${queryString({
    studentId: filters.studentId,
    teacherId: filters.teacherId,
    type: filters.type,
    from: filters.from,
    to: filters.to,
    page: filters.page,
    size: DEFAULT_PAGE_SIZE,
  })}`;
}

/** Relatórios aprovados da escola, do mais recente ao mais antigo (a ordem vem do servidor). */
export function useSchoolHistory(filters: SchoolHistoryFilters) {
  return useQuery<SchoolHistoryPage>({
    queryKey: schoolHistoryKey(filters),
    queryFn: ({ signal }) => api.get<SchoolHistoryPage>(schoolHistoryPath(filters), signal),
    placeholderData: keepPreviousData,
  });
}

export type SchoolPdfMode = 'open' | 'download';

/**
 * Caminho do PDF oficial da versão aprovada (abrir ou baixar) e o nome de reserva para salvar: o
 * nome sugerido pelo servidor (Content-Disposition) tem precedência em `openPdf`/`downloadPdf`.
 */
export function schoolReportPdf(
  item: Pick<SchoolHistoryItem, 'type' | 'reportId' | 'lessonDate' | 'periodStart'>,
  mode: SchoolPdfMode,
): { path: string; fileName: string } {
  const folder = item.type === 'SEMESTER' ? 'semester-reports' : 'lesson-reports';
  const base = `/admin/school-history/${folder}/${item.reportId}/pdf`;
  const path = mode === 'download' ? `${base}/download` : base;
  if (item.type === 'SEMESTER') {
    const start = item.periodStart ?? '';
    const semester = Number(start.slice(5, 7)) >= 7 ? 2 : 1;
    return { path, fileName: `relatorio-semestral-${start.slice(0, 4)}-${semester}.pdf` };
  }
  return { path, fileName: `relatorio-aula-${item.lessonDate ?? ''}.pdf` };
}

/** Tamanho máximo de página aceito pela API. */
const TEACHER_OPTIONS_SIZE = 100;

/**
 * Professores (ativos e inativos) para o filtro de professor; o inativo vem marcado pela tela.
 * Percorre todas as páginas, para que nenhum professor fique de fora do filtro.
 */
export function useSchoolHistoryTeachers() {
  return useQuery<TeacherPage>({
    queryKey: [ADMIN_QUERY_ROOT, 'school-history', 'teachers'],
    queryFn: async ({ signal }) => {
      const first = await api.get<TeacherPage>(
        `/admin/teachers${queryString({ page: 0, size: TEACHER_OPTIONS_SIZE })}`,
        signal,
      );
      const items = [...first.items];
      for (let page = 1; items.length < first.total; page += 1) {
        const next = await api.get<TeacherPage>(
          `/admin/teachers${queryString({ page, size: TEACHER_OPTIONS_SIZE })}`,
          signal,
        );
        if (next.items.length === 0) break;
        items.push(...next.items);
      }
      return { ...first, items };
    },
    staleTime: 60_000,
  });
}
