import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { ApiError, api, request } from '../apiClient';
import type { components } from '../administration-api-types';

export type AdministratorSession = components['schemas']['AdministratorSession'];

/** Raiz de todas as chaves de consulta da área administrativa (limpar só o cache do administrador). */
export const ADMIN_QUERY_ROOT = 'admin' as const;
export const ADMIN_SESSION_KEY = [ADMIN_QUERY_ROOT, 'session'] as const;

/** Rota de entrada do administrador guardando o destino (só dentro da área administrativa). */
export function adminSignInRoute(destination: string, reason?: string): string {
  const params = new URLSearchParams();
  if (isAdminDestination(destination) && destination !== '/admin') params.set('destination', destination);
  if (reason) params.set('reason', reason);
  const query = params.toString();
  return query ? `/admin/sign-in?${query}` : '/admin/sign-in';
}

/** Destino aceito após entrar: caminho interno da área administrativa; qualquer outro vira o início. */
export function isAdminDestination(destination: string | null | undefined): destination is string {
  return (
    typeof destination === 'string' &&
    /^\/admin(?:[/?#]|$)/.test(destination) &&
    !destination.startsWith('//') &&
    !destination.startsWith('/admin/sign-in')
  );
}

/** Esquece tudo da área administrativa e deixa a sessão como "sem sessão", sem tocar no cache do professor. */
export function forgetAdminSession(client: QueryClient): void {
  client.removeQueries({ queryKey: [ADMIN_QUERY_ROOT] });
  client.setQueryData(ADMIN_SESSION_KEY, null);
}

/**
 * Sessão atual do administrador; `null` quando não há sessão (401). Um 404 (credencial de outro papel)
 * segue como erro: a área não é revelada.
 */
export function useAdminSession() {
  return useQuery<AdministratorSession | null>({
    queryKey: ADMIN_SESSION_KEY,
    queryFn: async () => {
      try {
        return await request<AdministratorSession>('/admin/session', { ignore401: true });
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null;
        throw error;
      }
    },
    staleTime: 60_000,
    retry: false,
  });
}

export interface AdminCredentials {
  email: string;
  password: string;
}

export function useAdminSignIn() {
  const client = useQueryClient();
  return useMutation<AdministratorSession, ApiError, AdminCredentials>({
    mutationFn: (credentials) =>
      request<AdministratorSession>('/admin/session', { method: 'POST', body: credentials, ignore401: true }),
    gcTime: 0,
    onSuccess: (session) => {
      client.removeQueries({ queryKey: [ADMIN_QUERY_ROOT] });
      client.setQueryData(ADMIN_SESSION_KEY, session);
    },
  });
}

/** Encerra só esta sessão, no servidor; o portal só navega depois do 2xx. */
export function useAdminSignOut() {
  const client = useQueryClient();
  return useMutation<void, ApiError, void>({
    mutationFn: () => api.delete<void>('/admin/session'),
    onSuccess: () => forgetAdminSession(client),
  });
}

export interface Activation {
  email: string;
  activationCode: string;
  newPassword: string;
}

export function useActivateAdministrator() {
  return useMutation<void, ApiError, Activation>({
    mutationFn: (activation) =>
      request<void>('/admin/activation', { method: 'POST', body: activation, ignore401: true }),
    gcTime: 0,
  });
}

/** Troca da senha provisória: o servidor devolve a sessão nova (cookie novo, 2 h a partir da troca). */
export function useChangeProvisionalPassword() {
  const client = useQueryClient();
  return useMutation<AdministratorSession, ApiError, string>({
    mutationFn: (newPassword) => api.put<AdministratorSession>('/admin/password', { newPassword }),
    gcTime: 0,
    onSuccess: (session) => client.setQueryData(ADMIN_SESSION_KEY, session),
  });
}
