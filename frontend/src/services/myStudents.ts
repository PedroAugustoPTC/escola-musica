import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, api } from './apiClient';
import { HOME_KEY } from './home';
import type { components } from './administration-api-types';
import { looksLikeCpf, searchTermOf } from '../components/student/cpfShape';
import type { StudentFormFieldError } from '../components/student/studentFormTypes';
import type { StudentInputBody, StudentUpdateBody } from '../components/student/studentPayload';

type Schemas = components['schemas'];

export type MyStudent = Schemas['Student'];
export type MyStudentPage = Schemas['StudentPage'];
export type StudentStatus = 'ACTIVE' | 'INACTIVE' | 'ALL';

export const MY_STUDENTS_PAGE_SIZE = 20;
export const MY_STUDENTS_KEY = ['my-students'] as const;

export interface MyStudentsFilter {
  status: StudentStatus;
  /** Termo de busca por nome ou instrumento; nunca CPF (recusado antes, no cliente). */
  search: string;
  /** Base 0. */
  page: number;
}

function listPath(filter: MyStudentsFilter): string {
  const params = new URLSearchParams({
    status: filter.status,
    page: String(filter.page),
    size: String(MY_STUDENTS_PAGE_SIZE),
  });
  const search = searchTermOf(filter.search);
  if (search) params.set('search', search);
  return `/my-students?${params.toString()}`;
}

/** Alunos do professor da sessão (FR-025): lista paginada com busca e filtro de situação. */
export function useMyStudents(filter: MyStudentsFilter) {
  return useQuery<MyStudentPage>({
    // termo com forma de CPF nunca entra na chave de consulta nem na API (não existe busca por CPF)
    queryKey: [...MY_STUDENTS_KEY, 'list', filter.status, filter.page, searchTermOf(filter.search)],
    queryFn: ({ signal }) => api.get<MyStudentPage>(listPath(filter), signal),
    placeholderData: keepPreviousData,
    enabled: !looksLikeCpf(filter.search.trim()),
  });
}

/** Um aluno do professor (404 se inexistente ou de outro professor, sem distinção). */
export function useMyStudent(studentId: string | undefined) {
  return useQuery<MyStudent>({
    queryKey: [...MY_STUDENTS_KEY, 'detail', studentId],
    queryFn: ({ signal }) => api.get<MyStudent>(`/my-students/${studentId}`, signal),
    enabled: Boolean(studentId),
  });
}

/** Depois de gravar, a lista de "Meus alunos", a escolha de aluno para aula e o painel precisam recarregar. */
function useRefreshAfterWrite() {
  const client = useQueryClient();
  return (student?: MyStudent) => {
    if (student) client.setQueryData([...MY_STUDENTS_KEY, 'detail', student.id], student);
    void client.invalidateQueries({ queryKey: [...MY_STUDENTS_KEY, 'list'] });
    void client.invalidateQueries({ queryKey: ['students'] });
    void client.invalidateQueries({ queryKey: HOME_KEY });
  };
}

/**
 * Cadastro (FR-024). `gcTime: 0` porque as variáveis da mutação carregam o CPF: não podem
 * ficar no cache depois de usadas.
 */
export function useRegisterMyStudent() {
  const refresh = useRefreshAfterWrite();
  return useMutation<MyStudent, Error, StudentInputBody>({
    mutationFn: (body) => api.post<MyStudent>('/my-students', body),
    gcTime: 0,
    onSuccess: refresh,
  });
}

/** Edição (FR-025); mesma regra de `gcTime` do cadastro, pois o CPF pode ir no corpo. */
export function useUpdateMyStudent(studentId: string) {
  const refresh = useRefreshAfterWrite();
  return useMutation<MyStudent, Error, StudentUpdateBody>({
    mutationFn: (body) => api.patch<MyStudent>(`/my-students/${studentId}`, body),
    gcTime: 0,
    onSuccess: refresh,
  });
}

/** Inativar ou reativar (FR-026): o servidor nega com `SHARED_STUDENT_ACTION_DENIED` se houver mais de um professor ativo. */
export function useChangeMyStudentSituation(studentId: string) {
  const refresh = useRefreshAfterWrite();
  return useMutation<MyStudent, Error, 'inactivation' | 'reactivation'>({
    mutationFn: (action) => api.post<MyStudent>(`/my-students/${studentId}/${action}`),
    onSuccess: refresh,
  });
}

const CPF_ERROR_CODES = new Set(['CPF_UNAVAILABLE', 'STUDENT_ALREADY_YOURS']);

/**
 * Erros por campo de uma falha de gravação: `details.fields` da validação, ou o CPF recusado por
 * unicidade (mensagem neutra no campo, sem nome nem professor: nada além do código é usado).
 * No fluxo do professor `DUPLICATE_CPF` vale `CPF_UNAVAILABLE`: nunca "pertence ao aluno ...", para não
 * virar oráculo de existência de CPF.
 */
export function fieldErrorsOf(error: unknown): StudentFormFieldError[] {
  if (!(error instanceof ApiError)) return [];
  if (error.code === 'DUPLICATE_CPF') return [{ field: 'cpf', code: 'CPF_UNAVAILABLE' }];
  if (CPF_ERROR_CODES.has(error.code)) return [{ field: 'cpf', code: error.code }];
  const fields = error.details.fields;
  if (!Array.isArray(fields)) return [];
  return fields
    .filter((item): item is { field: string; code: string } => {
      const candidate = item as Partial<StudentFormFieldError> | null;
      return typeof candidate?.field === 'string' && typeof candidate.code === 'string';
    })
    .map(({ field, code }) => ({ field, code }));
}
