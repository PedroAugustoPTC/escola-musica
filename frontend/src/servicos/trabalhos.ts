import { useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, type Esquemas } from './clienteApi';

export type TrabalhoDto = Esquemas['TrabalhoDto'];

/** Intervalo do polling (R-06) e limite depois do qual a tela oferece voltar ao Painel. */
export const INTERVALO_POLLING_MS = 2000;
export const LIMITE_POLLING_MS = 10 * 60 * 1000;

export function trabalhoFinalizado(trabalho: TrabalhoDto | undefined): boolean {
  return (
    trabalho?.status === 'CONCLUIDO' || trabalho?.status === 'FALHOU' || trabalho?.status === 'DESCARTADO'
  );
}

/**
 * Acompanha um trabalho a cada 2 s até o estado final. O polling pausa com a aba oculta (o TanStack
 * Query não consulta em segundo plano) e para depois de 10 minutos; o trabalho continua no servidor.
 */
export function usePollingTrabalho(trabalhoId: string | undefined) {
  const inicio = useRef<{ id: string | undefined; em: number } | null>(null);
  const [excedidoEm, setExcedidoEm] = useState<string | null>(null);
  const excedeuLimite = Boolean(trabalhoId) && excedidoEm === trabalhoId;

  const consulta = useQuery<TrabalhoDto>({
    queryKey: ['trabalho', trabalhoId],
    queryFn: ({ signal }) => api.get<TrabalhoDto>(`/trabalhos/${trabalhoId}`, signal),
    enabled: Boolean(trabalhoId) && !excedeuLimite,
    refetchInterval: (query) => {
      if (trabalhoFinalizado(query.state.data)) return false;
      const agora = Date.now();
      if (!inicio.current || inicio.current.id !== trabalhoId) {
        inicio.current = { id: trabalhoId, em: agora };
      }
      if (agora - inicio.current.em >= LIMITE_POLLING_MS) {
        setExcedidoEm(trabalhoId ?? null);
        return false;
      }
      return INTERVALO_POLLING_MS;
    },
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });

  return { ...consulta, excedeuLimite, finalizado: trabalhoFinalizado(consulta.data) };
}
