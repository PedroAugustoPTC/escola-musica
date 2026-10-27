import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, type ApiError } from '../apiClient';
import type { components } from '../administration-api-types';
import { ADMIN_QUERY_ROOT } from './session';
import { adminStudentKey } from './students';

export type RemovalPreview = components['schemas']['RemovalPreview'];

const path = (studentId: string, teacherId: string) => `/admin/students/${studentId}/teachers/${teacherId}`;

/** Quantos relatórios em aberto do professor com o aluno ficariam congelados (FR-029). */
export function usePreviewAssignmentRemoval(studentId: string) {
  return useMutation<RemovalPreview, ApiError, string>({
    mutationFn: (teacherId) => api.get<RemovalPreview>(`${path(studentId, teacherId)}/removal-preview`),
    gcTime: 0,
  });
}

function useRefreshStudent(studentId: string) {
  const client = useQueryClient();
  return () => {
    void client.invalidateQueries({ queryKey: adminStudentKey(studentId) });
    void client.invalidateQueries({ queryKey: [ADMIN_QUERY_ROOT, 'students', 'list'] });
  };
}

/** Associa um professor ativo ao aluno (idempotente). */
export function useAssignTeacher(studentId: string) {
  const refresh = useRefreshStudent(studentId);
  return useMutation<void, ApiError, string>({
    mutationFn: (teacherId) => api.put<void>(path(studentId, teacherId)),
    onSuccess: refresh,
  });
}

/** Retira a associação: nunca é bloqueada; o acesso do professor ao aluno cai na hora. */
export function useRemoveTeacher(studentId: string) {
  const refresh = useRefreshStudent(studentId);
  return useMutation<void, ApiError, string>({
    mutationFn: (teacherId) => api.delete<void>(path(studentId, teacherId)),
    onSuccess: refresh,
  });
}
