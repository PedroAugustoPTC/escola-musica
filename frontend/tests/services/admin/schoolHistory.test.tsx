import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  schoolHistoryKey,
  schoolHistoryPath,
  schoolReportPdf,
  useSchoolHistory,
} from '../../../src/services/admin/schoolHistory';
import { mockApi } from '../../utils';
import { schoolHistoryItem, schoolHistoryPage } from '../../support/schoolHistoryFixtures';

const STUDENT_ID = '0190a000-0000-7000-8000-0000000000d1';
const TEACHER_ID = '0190a000-0000-7000-8000-0000000000b1';

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe('schoolHistory (serviço da visão da escola)', () => {
  it('o caminho leva só identificadores, tipo, datas e página', () => {
    const path = schoolHistoryPath({
      studentId: STUDENT_ID,
      teacherId: TEACHER_ID,
      type: 'SEMESTER',
      from: '2026-01-01',
      to: '2026-06-30',
      page: 2,
    });

    const url = new URL(path, 'http://localhost');
    expect(url.pathname).toBe('/admin/school-history');
    expect(Object.fromEntries(url.searchParams)).toEqual({
      studentId: STUDENT_ID,
      teacherId: TEACHER_ID,
      type: 'SEMESTER',
      from: '2026-01-01',
      to: '2026-06-30',
      page: '2',
      size: '20',
    });
  });

  it('filtros ausentes ficam fora do caminho', () => {
    const url = new URL(schoolHistoryPath({ page: 0 }), 'http://localhost');
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: '0', size: '20' });
  });

  it('a chave de consulta tem só identificadores, tipo, datas e página', () => {
    const key = schoolHistoryKey({ studentId: STUDENT_ID, type: 'LESSON', page: 1 });
    expect(key).toEqual([
      'admin',
      'school-history',
      'list',
      { studentId: STUDENT_ID, teacherId: null, type: 'LESSON', from: null, to: null, page: 1 },
    ]);
  });

  it('busca a página no servidor e a devolve', async () => {
    const page = schoolHistoryPage([schoolHistoryItem()]);
    const api = mockApi({ 'GET /api/v1/admin/school-history': { body: page } });

    const { result } = renderHook(() => useSchoolHistory({ studentId: STUDENT_ID, page: 0 }), { wrapper });

    await waitFor(() => expect(result.current.data).toEqual(page));
    expect(api.calls[0]!.url).toBe(`/api/v1/admin/school-history?studentId=${STUDENT_ID}&page=0&size=20`);
  });

  it('o PDF de aula usa as rotas /pdf e /pdf/download e o nome sugerido da aula', () => {
    const item = schoolHistoryItem({ type: 'LESSON', lessonDate: '2026-03-10' });
    expect(schoolReportPdf(item, 'open')).toEqual({
      path: `/admin/school-history/lesson-reports/${item.reportId}/pdf`,
      fileName: 'relatorio-aula-2026-03-10.pdf',
    });
    expect(schoolReportPdf(item, 'download').path).toBe(
      `/admin/school-history/lesson-reports/${item.reportId}/pdf/download`,
    );
  });

  it('o PDF semestral usa a pasta semester-reports e o nome do semestre', () => {
    const item = schoolHistoryItem({
      type: 'SEMESTER',
      lessonDate: undefined,
      periodStart: '2026-07-01',
      periodEnd: '2026-12-15',
    });
    expect(schoolReportPdf(item, 'open')).toEqual({
      path: `/admin/school-history/semester-reports/${item.reportId}/pdf`,
      fileName: 'relatorio-semestral-2026-2.pdf',
    });
    expect(schoolReportPdf(item, 'download').path).toBe(
      `/admin/school-history/semester-reports/${item.reportId}/pdf/download`,
    );
  });
});
