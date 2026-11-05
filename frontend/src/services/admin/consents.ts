import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../apiClient';
import type { components } from '../privacy-api-types';
import { ADMIN_QUERY_ROOT } from './session';
import { DEFAULT_PAGE_SIZE, queryString } from './query';

type Schemas = components['schemas'];

export type ConsentInput = Schemas['ConsentInput'];
export type ConsentSummary = Schemas['ConsentSummary'];
export type ConsentTerm = Schemas['ConsentTerm'];

/** Motivo de o menor estar sem consentimento válido. */
export type PendingReason = 'MISSING' | 'REVOKED' | 'SUPERSEDED';

export interface PendingConsent {
  studentId: string;
  /** Resolvido na exibição; só esta tela do administrador mostra o nome. */
  studentName: string;
  reason: PendingReason;
}

export interface PendingConsentPage {
  items: PendingConsent[];
  page: number;
  size: number;
  total: number;
}

/** Quem revogou, por papel (sem nome). */
export type RevokerRole = 'MOTHER' | 'FATHER' | 'LEGAL_GUARDIAN' | 'OTHER' | 'STUDENT';

const CONSENTS = [ADMIN_QUERY_ROOT, 'consents'] as const;
export const studentConsentsKey = (studentId: string) => [...CONSENTS, 'student', studentId] as const;
export const CONSENT_TERMS_KEY = [...CONSENTS, 'terms'] as const;
export const PENDING_CONSENTS_KEY = [...CONSENTS, 'pending'] as const;

/** Histórico de consentimentos do aluno, do mais recente ao mais antigo (CONSENT_READ). */
export function useStudentConsents(studentId: string | undefined) {
  return useQuery<ConsentSummary[]>({
    queryKey: studentConsentsKey(studentId ?? ''),
    queryFn: ({ signal }) => api.get<ConsentSummary[]>(`/admin/students/${studentId}/consents`, signal),
    enabled: Boolean(studentId),
  });
}

function useRefreshConsents() {
  const client = useQueryClient();
  return () => {
    void client.invalidateQueries({ queryKey: CONSENTS });
  };
}

/** Registra novo consentimento (o anterior fica como histórico). `gcTime: 0`: o corpo leva o nome de quem consentiu. */
export function useRecordConsent(studentId: string) {
  const refresh = useRefreshConsents();
  return useMutation<ConsentSummary, Error, ConsentInput>({
    mutationFn: (body) => api.post<ConsentSummary>(`/admin/students/${studentId}/consents`, body),
    gcTime: 0,
    onSuccess: refresh,
  });
}

/** Registra a revogação: data e quem revogou, por papel. */
export function useRevokeConsent() {
  const refresh = useRefreshConsents();
  return useMutation<ConsentSummary, Error, { consentId: string; revokedOn: string; revokedBy: RevokerRole }>({
    mutationFn: ({ consentId, revokedOn, revokedBy }) =>
      api.post<ConsentSummary>(`/admin/consents/${consentId}/revocation`, { revokedOn, revokedBy }),
    onSuccess: refresh,
  });
}

/** Menores sem consentimento válido (vazia em operação normal). */
export function usePendingConsents(page: number) {
  return useQuery<PendingConsentPage>({
    queryKey: [...PENDING_CONSENTS_KEY, page],
    queryFn: ({ signal }) =>
      api.get<PendingConsentPage>(
        `/admin/consents/pending${queryString({ page, size: DEFAULT_PAGE_SIZE })}`,
        signal,
      ),
    placeholderData: keepPreviousData,
  });
}

/** Versões do termo, da mais recente à mais antiga. */
export function useConsentTerms() {
  return useQuery<ConsentTerm[]>({
    queryKey: CONSENT_TERMS_KEY,
    queryFn: ({ signal }) => api.get<ConsentTerm[]>('/admin/consent-terms', signal),
  });
}

export interface PublishTermBody {
  publishedOn: string;
  text: string;
  requiresNewConsent: boolean;
}

/** Publica uma nova versão do termo; se exigir novo consentimento, as pendências aumentam. */
export function usePublishConsentTerm() {
  const refresh = useRefreshConsents();
  return useMutation<ConsentTerm, Error, PublishTermBody>({
    mutationFn: (body) => api.post<ConsentTerm>('/admin/consent-terms', body),
    onSuccess: refresh,
  });
}

/** Quantos alunos menores ficam pendentes se um termo que exige novo consentimento for publicado. */
export function useConsentTermImpact(enabled: boolean) {
  return useQuery<{ studentsAffected: number }>({
    queryKey: [...CONSENT_TERMS_KEY, 'impact'],
    queryFn: ({ signal }) => api.get<{ studentsAffected: number }>('/admin/consent-terms/impact', signal),
    enabled,
    gcTime: 0,
  });
}
