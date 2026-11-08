import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { api } from '../apiClient';
import type { components } from '../privacy-api-types';
import { ADMIN_QUERY_ROOT } from './session';
import { DEFAULT_PAGE_SIZE, queryString } from './query';

type Schemas = components['schemas'];

export type AuditEvent = Schemas['AuditEvent'];
export type AuditEventPage = Schemas['AuditEventPage'];
export type AuditIntegrity = Schemas['AuditIntegrity'];

/** Filtros da consulta: período, evento, resultado, canal e alvo (só identificadores); nunca nome nem CPF. */
export interface AuditTrailFilters {
  /** `AAAA-MM-DD`, inclusive. */
  from: string;
  /** `AAAA-MM-DD`, inclusive; no máximo 89 dias depois de `from`. */
  to: string;
  operation?: string;
  result?: string;
  origin?: string;
  targetId?: string;
  page: number;
}

const AUDIT = [ADMIN_QUERY_ROOT, 'audit-trail'] as const;

/** Chave de consulta só com os filtros e a página. */
export function auditTrailKey(filters: AuditTrailFilters) {
  return [
    ...AUDIT,
    'list',
    {
      from: filters.from,
      to: filters.to,
      operation: filters.operation ?? null,
      result: filters.result ?? null,
      origin: filters.origin ?? null,
      targetId: filters.targetId ?? null,
      page: filters.page,
    },
  ] as const;
}

/** Caminho da consulta (relativo a `/api/v1`). */
export function auditTrailPath(filters: AuditTrailFilters): string {
  return `/admin/audit-trail${queryString({
    from: filters.from,
    to: filters.to,
    operation: filters.operation,
    result: filters.result,
    origin: filters.origin,
    targetId: filters.targetId,
    page: filters.page,
    size: DEFAULT_PAGE_SIZE,
  })}`;
}

/** Eventos da trilha, do mais recente ao mais antigo (a ordem vem do servidor). */
export function useAuditTrail(filters: AuditTrailFilters, enabled = true) {
  return useQuery<AuditEventPage>({
    queryKey: auditTrailKey(filters),
    queryFn: ({ signal }) => api.get<AuditEventPage>(auditTrailPath(filters), signal),
    placeholderData: keepPreviousData,
    enabled,
  });
}

/** Resultado da última verificação da cadeia. */
export function useAuditIntegrity() {
  return useQuery<AuditIntegrity>({
    queryKey: [...AUDIT, 'integrity'],
    queryFn: ({ signal }) => api.get<AuditIntegrity>('/admin/audit-trail/integrity', signal),
  });
}

/** Verifica a cadeia agora (a verificação também vira evento da trilha). */
export function useVerifyAuditIntegrity() {
  return useMutation<AuditIntegrity>({
    mutationFn: () => api.get<AuditIntegrity>('/admin/audit-trail/integrity?verifyNow=true'),
  });
}
