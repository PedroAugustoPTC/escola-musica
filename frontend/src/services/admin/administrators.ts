import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type ApiError } from '../apiClient';
import type { components } from '../administration-api-types';
import { ADMIN_QUERY_ROOT } from './session';
import { DEFAULT_PAGE_SIZE, queryString } from './query';
import type { TeacherActiveFilter } from './teachers';

type Schemas = components['schemas'];

export type Administrator = Schemas['Administrator'];
export type AdministratorInput = Schemas['AdministratorInput'];
export type AdministratorPage = Schemas['AdministratorPage'];
export type AdministratorWithActivationCode = Schemas['AdministratorWithActivationCode'];

const ADMINISTRATORS = [ADMIN_QUERY_ROOT, 'administrators'] as const;

export interface AdministratorListParams {
  filter: TeacherActiveFilter;
  page: number;
}

/** Lista de administradores com filtro de situação e paginação (FR-032). */
export function useAdministrators({ filter, page }: AdministratorListParams) {
  const active = filter === 'all' ? undefined : filter === 'active';
  return useQuery<AdministratorPage>({
    queryKey: [...ADMINISTRATORS, 'list', filter, page],
    queryFn: ({ signal }) =>
      api.get<AdministratorPage>(
        `/admin/administrators${queryString({ active, page, size: DEFAULT_PAGE_SIZE })}`,
        signal,
      ),
    placeholderData: keepPreviousData,
  });
}

function useRefreshAdministrators() {
  const client = useQueryClient();
  return () => {
    void client.invalidateQueries({ queryKey: [...ADMINISTRATORS, 'list'] });
    void client.invalidateQueries({ queryKey: [ADMIN_QUERY_ROOT, 'counts'] });
  };
}

/** Cria o administrador; a resposta traz o código de ativação, que só vive na tela (`gcTime: 0`). */
export function useCreateAdministrator() {
  const refresh = useRefreshAdministrators();
  return useMutation<AdministratorWithActivationCode, ApiError, AdministratorInput>({
    mutationFn: (input) => api.post<AdministratorWithActivationCode>('/admin/administrators', input),
    gcTime: 0,
    onSuccess: refresh,
  });
}

/** Inativa e encerra as sessões do inativado; recusa (`LAST_ADMINISTRATOR`) o último administrador ativo. */
export function useInactivateAdministrator() {
  const refresh = useRefreshAdministrators();
  return useMutation<Administrator, ApiError, string>({
    mutationFn: (administratorId) =>
      api.post<Administrator>(`/admin/administrators/${administratorId}/inactivation`),
    onSuccess: refresh,
  });
}

/** Reativa com código de ativação novo, exibido uma única vez. */
export function useReactivateAdministrator() {
  const refresh = useRefreshAdministrators();
  return useMutation<AdministratorWithActivationCode, ApiError, string>({
    mutationFn: (administratorId) =>
      api.post<AdministratorWithActivationCode>(`/admin/administrators/${administratorId}/reactivation`),
    gcTime: 0,
    onSuccess: refresh,
  });
}
