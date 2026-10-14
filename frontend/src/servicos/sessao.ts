import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ErroApi, api, requisitar, type Esquemas } from './clienteApi';

export type SessaoDto = Esquemas['SessionDto'];

export const CHAVE_SESSAO = ['sessao'] as const;

/** Sessão atual; `null` quando não há sessão (401). */
export function useSessao() {
  return useQuery<SessaoDto | null>({
    queryKey: CHAVE_SESSAO,
    queryFn: async () => {
      try {
        return await requisitar<SessaoDto>('/session', { ignorar401: true });
      } catch (erro) {
        if (erro instanceof ErroApi && erro.status === 401) return null;
        throw erro;
      }
    },
    staleTime: 60_000,
    retry: false,
  });
}

export function useEntrar() {
  const cliente = useQueryClient();
  return useMutation<SessaoDto, ErroApi, string>({
    mutationFn: (codigo) =>
      requisitar<SessaoDto>('/session', { metodo: 'POST', corpo: { code: codigo }, ignorar401: true }),
    onSuccess: (sessao) => {
      cliente.clear();
      cliente.setQueryData(CHAVE_SESSAO, sessao);
    },
  });
}

/** Encerra a sessão no servidor; só depois de 2xx o portal navega (FR-053). */
export function useEncerrarSessao() {
  const cliente = useQueryClient();
  return useMutation<void, ErroApi, void>({
    mutationFn: () => api.delete<void>('/session'),
    onSuccess: () => {
      cliente.clear();
      cliente.setQueryData(CHAVE_SESSAO, null);
    },
  });
}
