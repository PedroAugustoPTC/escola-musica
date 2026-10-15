import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, type Schemas } from './apiClient';

export type StudentSummaryDto = Schemas['StudentSummaryDto'];
export type StudentSearchDto = Schemas['StudentSearchDto'];

/** Alunos associados com busca aproximada (FR-013). */
export function useStudents(search: string) {
  const term = search.trim();
  return useQuery<StudentSearchDto>({
    queryKey: ['students', term],
    queryFn: ({ signal }) =>
      api.get<StudentSearchDto>(term ? `/students?search=${encodeURIComponent(term)}` : '/students', signal),
    placeholderData: keepPreviousData,
  });
}

/** Resumo de um aluno associado (403 se não for do professor). */
export function useStudent(studentId: string | undefined) {
  return useQuery<StudentSummaryDto>({
    queryKey: ['student', studentId],
    queryFn: ({ signal }) => api.get<StudentSummaryDto>(`/students/${studentId}`, signal),
    enabled: Boolean(studentId),
  });
}
