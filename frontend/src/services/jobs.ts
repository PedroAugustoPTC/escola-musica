import { useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, type Schemas } from './apiClient';

export type JobDto = Schemas['JobDto'];

/** Intervalo do polling (R-06) e limite depois do qual a tela oferece voltar ao Painel. */
export const INTERVAL_POLLING_MS = 2000;
export const LIMIT_POLLING_MS = 10 * 60 * 1000;

export function finalizedJob(job: JobDto | undefined): boolean {
  return job?.status === 'COMPLETED' || job?.status === 'FAILED' || job?.status === 'DISCARDED';
}

/**
 * Acompanha um trabalho a cada 2 s até o estado final. O polling pausa com a aba oculta (o TanStack
 * Query não consulta em segundo plano) e para depois de 10 minutos; o trabalho continua no servidor.
 */
export function useJobPolling(jobId: string | undefined) {
  const start = useRef<{ id: string | undefined; em: number } | null>(null);
  const [exceededAt, setExceededAt] = useState<string | null>(null);
  const limitExceeded = Boolean(jobId) && exceededAt === jobId;

  const query = useQuery<JobDto>({
    queryKey: ['job', jobId],
    queryFn: ({ signal }) => api.get<JobDto>(`/jobs/${jobId}`, signal),
    enabled: Boolean(jobId) && !limitExceeded,
    refetchInterval: (query) => {
      if (finalizedJob(query.state.data)) return false;
      const now = Date.now();
      if (!start.current || start.current.id !== jobId) {
        start.current = { id: jobId, em: now };
      }
      if (now - start.current.em >= LIMIT_POLLING_MS) {
        setExceededAt(jobId ?? null);
        return false;
      }
      return INTERVAL_POLLING_MS;
    },
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });

  return { ...query, limitExceeded, finalized: finalizedJob(query.data) };
}
