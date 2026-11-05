import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../apiClient';
import type { components } from '../privacy-api-types';
import { ADMIN_QUERY_ROOT } from './session';
import { DEFAULT_PAGE_SIZE, queryString } from './query';

type Schemas = components['schemas'];

export type ErasureRequestInput = Schemas['ErasureRequestInput'];
export type ErasureRequest = Schemas['ErasureRequest'];
export type ErasureStatus = Schemas['ErasureStatus'];
export type ErasurePreview = Schemas['ErasurePreview'];
export type ErasureReceipt = Schemas['ErasureReceipt'];
export type ErasureScope = Schemas['ErasureScope'];

export interface ErasureRequestPage {
  items: ErasureRequest[];
  page: number;
  size: number;
  total: number;
}

const ERASURES = [ADMIN_QUERY_ROOT, 'erasures'] as const;

/** Pedidos de eliminação, do mais recente ao mais antigo (ERASURE_REQUEST_READ). */
export function useErasureRequests(page: number, status?: ErasureStatus) {
  return useQuery<ErasureRequestPage>({
    queryKey: [...ERASURES, 'list', status ?? 'ALL', page],
    queryFn: ({ signal }) =>
      api.get<ErasureRequestPage>(
        `/admin/erasure-requests${queryString({ status, page, size: DEFAULT_PAGE_SIZE })}`,
        signal,
      ),
    placeholderData: keepPreviousData,
    gcTime: 0,
  });
}

export function useErasureRequest(requestId: string | undefined) {
  return useQuery<ErasureRequest>({
    queryKey: [...ERASURES, 'one', requestId ?? ''],
    queryFn: ({ signal }) => api.get<ErasureRequest>(`/admin/erasure-requests/${requestId}`, signal),
    enabled: Boolean(requestId),
    gcTime: 0,
  });
}

/** Comprovante: sem nome, CPF nem dado do aluno. */
export function useErasureReceipt(requestId: string | undefined) {
  return useQuery<ErasureReceipt>({
    queryKey: [...ERASURES, 'receipt', requestId ?? ''],
    queryFn: ({ signal }) => api.get<ErasureReceipt>(`/admin/erasure-requests/${requestId}/receipt`, signal),
    enabled: Boolean(requestId),
    gcTime: 0,
  });
}

function useRefreshErasures() {
  const client = useQueryClient();
  return () => {
    void client.invalidateQueries({ queryKey: ERASURES });
  };
}

/** Registra o pedido. `gcTime: 0`: o corpo leva o nome de quem pediu. */
export function useRegisterErasure(studentId: string | undefined) {
  const refresh = useRefreshErasures();
  return useMutation<ErasureRequest, Error, ErasureRequestInput>({
    mutationFn: (body) => api.post<ErasureRequest>(`/admin/students/${studentId}/erasure-requests`, body),
    gcTime: 0,
    onSuccess: refresh,
  });
}

/** Mostra o alcance e emite um token novo (o anterior deixa de valer). O token vive só na memória do componente. */
export function usePreviewErasure(requestId: string | undefined) {
  return useMutation<ErasurePreview, Error, void>({
    mutationFn: () => api.post<ErasurePreview>(`/admin/erasure-requests/${requestId}/preview`, {}),
    gcTime: 0,
  });
}

export function useConfirmErasure(requestId: string | undefined) {
  const refresh = useRefreshErasures();
  return useMutation<ErasureRequest, Error, { confirmationToken: string; confirmationWord: string }>({
    mutationFn: (body) => api.post<ErasureRequest>(`/admin/erasure-requests/${requestId}/confirmation`, body),
    gcTime: 0,
    onSuccess: refresh,
  });
}
