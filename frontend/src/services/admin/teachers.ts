import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type ApiError } from '../apiClient';
import type { components } from '../administration-api-types';
import { ADMIN_QUERY_ROOT } from './session';
import { DEFAULT_PAGE_SIZE, queryString } from './query';

type Schemas = components['schemas'];

export type Teacher = Schemas['Teacher'];
export type TeacherInput = Schemas['TeacherInput'];
export type TeacherPage = Schemas['TeacherPage'];
export type TeacherWithCode = Schemas['TeacherWithCode'];

export type TeacherActiveFilter = 'all' | 'active' | 'inactive';

const TEACHERS = [ADMIN_QUERY_ROOT, 'teachers'] as const;
const teacherKey = (id: string) => [...TEACHERS, 'detail', id] as const;

export interface TeacherListParams {
  search: string;
  filter: TeacherActiveFilter;
  page: number;
}

/** Lista de professores com busca, filtro de situação e paginação (FR-012). */
export function useTeachers({ search, filter, page }: TeacherListParams) {
  const term = search.trim();
  const active = filter === 'all' ? undefined : filter === 'active';
  return useQuery<TeacherPage>({
    queryKey: [...TEACHERS, 'list', term, filter, page],
    queryFn: ({ signal }) =>
      api.get<TeacherPage>(
        `/admin/teachers${queryString({ search: term, active, page, size: DEFAULT_PAGE_SIZE })}`,
        signal,
      ),
    placeholderData: keepPreviousData,
  });
}

export function useTeacher(teacherId: string | undefined) {
  return useQuery<Teacher>({
    queryKey: teacherKey(teacherId ?? ''),
    queryFn: ({ signal }) => api.get<Teacher>(`/admin/teachers/${teacherId}`, signal),
    enabled: Boolean(teacherId),
  });
}

/** Passa o professor devolvido a quem o consulta e invalida as listas; o código nunca entra no cache. */
function useRememberTeacher() {
  const client = useQueryClient();
  return (teacher: Teacher) => {
    client.setQueryData(teacherKey(teacher.id), teacher);
    void client.invalidateQueries({ queryKey: [...TEACHERS, 'list'] });
    void client.invalidateQueries({ queryKey: [ADMIN_QUERY_ROOT, 'counts'] });
  };
}

/** Cadastra o professor; a resposta traz o código de acesso, que só vive na tela (`gcTime: 0`). */
export function useRegisterTeacher() {
  const remember = useRememberTeacher();
  return useMutation<TeacherWithCode, ApiError, TeacherInput>({
    mutationFn: (input) => api.post<TeacherWithCode>('/admin/teachers', input),
    gcTime: 0,
    onSuccess: (result) => remember(result.teacher),
  });
}

export function useUpdateTeacher(teacherId: string) {
  const remember = useRememberTeacher();
  return useMutation<Teacher, ApiError, TeacherInput>({
    mutationFn: (input) => api.patch<Teacher>(`/admin/teachers/${teacherId}`, input),
    onSuccess: remember,
  });
}

export function useInactivateTeacher(teacherId: string) {
  const remember = useRememberTeacher();
  return useMutation<Teacher, ApiError, void>({
    mutationFn: () => api.post<Teacher>(`/admin/teachers/${teacherId}/inactivation`),
    onSuccess: remember,
  });
}

/** Reativa com código novo, exibido uma única vez. */
export function useReactivateTeacher(teacherId: string) {
  const remember = useRememberTeacher();
  return useMutation<TeacherWithCode, ApiError, void>({
    mutationFn: () => api.post<TeacherWithCode>(`/admin/teachers/${teacherId}/reactivation`),
    gcTime: 0,
    onSuccess: (result) => remember(result.teacher),
  });
}

/** Reemite o código de acesso (FR-030); `endTelegramLink` encerra também o vínculo do Telegram. */
export function useReissueAccessCode(teacherId: string) {
  const remember = useRememberTeacher();
  return useMutation<TeacherWithCode, ApiError, { endTelegramLink: boolean }>({
    mutationFn: (request) => api.post<TeacherWithCode>(`/admin/teachers/${teacherId}/access-code`, request),
    gcTime: 0,
    onSuccess: (result) => remember(result.teacher),
  });
}

/** Desvincula o Telegram do professor (FR-031). */
export function useUnlinkTeacherTelegram(teacherId: string) {
  const client = useQueryClient();
  return useMutation<void, ApiError, void>({
    mutationFn: () => api.delete<void>(`/admin/teachers/${teacherId}/telegram-link`),
    onSuccess: () => {
      client.setQueryData<Teacher>(teacherKey(teacherId), (current) =>
        current ? { ...current, telegramLinked: false } : current,
      );
      void client.invalidateQueries({ queryKey: [...TEACHERS, 'list'] });
    },
  });
}
