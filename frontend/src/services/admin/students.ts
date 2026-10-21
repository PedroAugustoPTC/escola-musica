import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type ApiError } from '../apiClient';
import type { components } from '../administration-api-types';
import type { StudentFormValues } from '../../components/student/studentFormTypes';
import { ADMIN_QUERY_ROOT } from './session';
import { DEFAULT_PAGE_SIZE, queryString } from './query';

type Schemas = components['schemas'];

export type AdminStudent = Schemas['Student'];
export type AdminStudentPage = Schemas['StudentPage'];
export type AdminStudentInput = Schemas['AdministratorStudentInput'];
export type AdminStudentUpdate = Schemas['StudentUpdate'];

export type StudentStatus = 'ACTIVE' | 'INACTIVE' | 'ALL';

const STUDENTS = [ADMIN_QUERY_ROOT, 'students'] as const;
export const adminStudentKey = (id: string) => [...STUDENTS, 'detail', id] as const;

/**
 * Termo com forma de CPF (11 dígitos, com ou sem pontos, traço e espaços). Não existe busca por CPF: o
 * termo é recusado no cliente, antes de ir à URL, à API ou à chave de consulta.
 */
export function looksLikeCpf(term: string): boolean {
  return /^\d{11}$/.test(term.replace(/[.\-\s]/g, ''));
}

export interface StudentListParams {
  search: string;
  status: StudentStatus;
  page: number;
}

/** Lista de alunos de toda a escola (FR-021). */
export function useAdminStudents({ search, status, page }: StudentListParams) {
  const term = search.trim();
  return useQuery<AdminStudentPage>({
    queryKey: [...STUDENTS, 'list', term, status, page],
    queryFn: ({ signal }) =>
      api.get<AdminStudentPage>(
        `/admin/students${queryString({
          search: term,
          status: status === 'ACTIVE' ? undefined : status,
          page,
          size: DEFAULT_PAGE_SIZE,
        })}`,
        signal,
      ),
    placeholderData: keepPreviousData,
    // um termo com forma de CPF nunca chega aqui (a tela o recusa), mas a guarda é dupla
    enabled: !looksLikeCpf(term),
  });
}

export function useAdminStudent(studentId: string | undefined) {
  return useQuery<AdminStudent>({
    queryKey: adminStudentKey(studentId ?? ''),
    queryFn: ({ signal }) => api.get<AdminStudent>(`/admin/students/${studentId}`, signal),
    enabled: Boolean(studentId),
  });
}

function filled(value: string): string | undefined {
  const text = value.trim();
  return text === '' ? undefined : text;
}

/** Responsável só segue se algum dos três campos foi preenchido (o servidor decide se é obrigatório). */
function guardianOf(values: StudentFormValues): Schemas['GuardianInput'] | undefined {
  if (!filled(values.guardianName) && !filled(values.guardianPhone) && !filled(values.guardianEmail)) {
    return undefined;
  }
  return {
    name: values.guardianName.trim(),
    phone: values.guardianPhone.trim(),
    email: values.guardianEmail.trim(),
  };
}

/** Corpo do cadastro a partir do formulário; formato apenas, sem regra (a validação é do servidor). */
export function toStudentInput(values: StudentFormValues): AdminStudentInput {
  const guardian = guardianOf(values);
  const instrument = filled(values.instrument);
  return {
    name: values.name.trim(),
    birthDate: values.birthDate,
    cpf: values.cpf.trim(),
    ...(instrument ? { instrument } : {}),
    ...(guardian ? { guardian } : {}),
    teacherIds: values.teacherIds,
  };
}

/** Corpo da edição: CPF em branco não é enviado (mantém o atual). */
export function toStudentUpdate(values: StudentFormValues): AdminStudentUpdate {
  const guardian = guardianOf(values);
  const instrument = filled(values.instrument);
  const cpf = filled(values.cpf);
  return {
    name: values.name.trim(),
    birthDate: values.birthDate,
    ...(cpf ? { cpf } : {}),
    ...(instrument ? { instrument } : {}),
    ...(guardian ? { guardian } : {}),
  };
}

function useRememberStudent() {
  const client = useQueryClient();
  return (student: AdminStudent) => {
    client.setQueryData(adminStudentKey(student.id), student);
    void client.invalidateQueries({ queryKey: [...STUDENTS, 'list'] });
    void client.invalidateQueries({ queryKey: [ADMIN_QUERY_ROOT, 'counts'] });
  };
}

/** Cadastra o aluno; o CPF só vive no corpo da requisição (a mutação não guarda os valores: `gcTime: 0`). */
export function useRegisterAdminStudent() {
  const remember = useRememberStudent();
  return useMutation<AdminStudent, ApiError, StudentFormValues>({
    mutationFn: (values) => api.post<AdminStudent>('/admin/students', toStudentInput(values)),
    gcTime: 0,
    onSuccess: remember,
  });
}

export function useUpdateAdminStudent(studentId: string) {
  const remember = useRememberStudent();
  return useMutation<AdminStudent, ApiError, StudentFormValues>({
    mutationFn: (values) => api.patch<AdminStudent>(`/admin/students/${studentId}`, toStudentUpdate(values)),
    gcTime: 0,
    onSuccess: remember,
  });
}

export function useInactivateAdminStudent(studentId: string) {
  const remember = useRememberStudent();
  return useMutation<AdminStudent, ApiError, void>({
    mutationFn: () => api.post<AdminStudent>(`/admin/students/${studentId}/inactivation`),
    onSuccess: remember,
  });
}

export function useReactivateAdminStudent(studentId: string) {
  const remember = useRememberStudent();
  return useMutation<AdminStudent, ApiError, void>({
    mutationFn: () => api.post<AdminStudent>(`/admin/students/${studentId}/reactivation`),
    onSuccess: remember,
  });
}
