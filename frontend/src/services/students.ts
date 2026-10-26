import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, type Schemas } from './apiClient';
import { looksLikeCpf, searchTermOf } from '../components/student/cpfShape';

export type StudentSummaryDto = Schemas['StudentSummaryDto'];
export type StudentSearchDto = Schemas['StudentSearchDto'];

/** Alunos associados com busca aproximada (FR-013). */
export function useStudents(search: string) {
  // termo com forma de CPF nunca entra na chave de consulta nem na API (não existe busca por CPF)
  const term = searchTermOf(search);
  return useQuery<StudentSearchDto>({
    queryKey: ['students', term],
    queryFn: ({ signal }) =>
      api.get<StudentSearchDto>(term ? `/students?search=${encodeURIComponent(term)}` : '/students', signal),
    placeholderData: keepPreviousData,
    enabled: !looksLikeCpf(search.trim()),
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
