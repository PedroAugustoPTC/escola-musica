import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../apiClient';
import type { components } from '../privacy-api-types';
import { ADMIN_QUERY_ROOT } from './session';
import { DEFAULT_PAGE_SIZE, queryString } from './query';

type Schemas = components['schemas'];

export type MonthlyCostReport = Schemas['MonthlyCostReport'];
export type CostAggregate = Schemas['CostAggregate'];
export type StudentCost = Schemas['StudentCost'];
export type OperationalAlert = Schemas['OperationalAlert'];
export type EffectivePolicy = Schemas['EffectivePolicy'];

export interface OperationalAlertPage {
  items: OperationalAlert[];
  page: number;
  size: number;
  total: number;
  effectivePolicy: EffectivePolicy;
}

/** Intervalo da consulta do indicador do cabeçalho (FR-056: o alerta aparece em até 5 minutos). */
export const ALERT_SUMMARY_INTERVAL_MS = 60_000;

const OPERATIONS = [ADMIN_QUERY_ROOT, 'operations'] as const;
export const ALERT_SUMMARY_KEY = [...OPERATIONS, 'alert-summary'] as const;
export const ALERTS_KEY = [...OPERATIONS, 'alerts'] as const;

/** Tempo e custo do mês (`AAAA-MM`, no fuso da escola). */
export function useMonthlyCosts(month: string) {
  return useQuery<MonthlyCostReport>({
    queryKey: [...OPERATIONS, 'costs', month],
    queryFn: ({ signal }) =>
      api.get<MonthlyCostReport>(`/admin/operations/costs${queryString({ month })}`, signal),
    placeholderData: keepPreviousData,
  });
}

/** Alertas ativos (primeiro) e histórico, com os prazos e limites efetivos. */
export function useOperationalAlerts(page: number) {
  return useQuery<OperationalAlertPage>({
    queryKey: [...ALERTS_KEY, page],
    queryFn: ({ signal }) =>
      api.get<OperationalAlertPage>(
        `/admin/operations/alerts${queryString({ page, size: DEFAULT_PAGE_SIZE })}`,
        signal,
      ),
    placeholderData: keepPreviousData,
  });
}

/**
 * Quantos alertas estão ativos, para o indicador do cabeçalho. Consulta ao navegar e a cada 60 s,
 * mas só com a aba visível (nunca em segundo plano).
 */
export function useAlertSummary() {
  return useQuery<{ activeCount: number }>({
    queryKey: ALERT_SUMMARY_KEY,
    queryFn: ({ signal }) =>
      api.get<{ activeCount: number }>('/admin/operations/alerts/summary', signal),
    refetchInterval: () =>
      typeof document !== 'undefined' && document.visibilityState === 'visible'
        ? ALERT_SUMMARY_INTERVAL_MS
        : false,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
}

/** Marca como apurada a quebra da trilha; os demais tipos normalizam sozinhos. */
export function useAcknowledgeAlert() {
  const client = useQueryClient();
  return useMutation<OperationalAlert, Error, string>({
    mutationFn: (alertId) =>
      api.post<OperationalAlert>(`/admin/operations/alerts/${alertId}/acknowledgement`, {}),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ALERTS_KEY });
      void client.invalidateQueries({ queryKey: ALERT_SUMMARY_KEY });
    },
  });
}
