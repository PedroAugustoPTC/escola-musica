import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, api, request, type Schemas } from './apiClient';

export type SessionDto = Schemas['SessionDto'];

export const SESSION_KEY = ['session'] as const;

/** Sessão atual; `null` quando não há sessão (401). */
export function useSession() {
  return useQuery<SessionDto | null>({
    queryKey: SESSION_KEY,
    queryFn: async () => {
      try {
        return await request<SessionDto>('/session', { ignore401: true });
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null;
        throw error;
      }
    },
    staleTime: 60_000,
    retry: false,
  });
}

export function useSignIn() {
  const client = useQueryClient();
  return useMutation<SessionDto, ApiError, string>({
    mutationFn: (code) =>
      request<SessionDto>('/session', { method: 'POST', body: { code }, ignore401: true }),
    onSuccess: (session) => {
      client.clear();
      client.setQueryData(SESSION_KEY, session);
    },
  });
}

/** Encerra a sessão no servidor; só depois de 2xx o portal navega (FR-053). */
export function useEndSession() {
  const client = useQueryClient();
  return useMutation<void, ApiError, void>({
    mutationFn: () => api.delete<void>('/session'),
    onSuccess: () => {
      client.clear();
      client.setQueryData(SESSION_KEY, null);
    },
  });
}
