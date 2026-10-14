import { useQuery } from '@tanstack/react-query';
import { api, type Esquemas } from './clienteApi';

export type InicioDto = Esquemas['HomeDto'];
export type RelatorioEmAbertoDto = Esquemas['OpenReportDto'];

export const CHAVE_INICIO = ['inicio'] as const;

/** Dados do Painel (FR-012, FR-035). */
export function useInicio() {
  return useQuery<InicioDto>({
    queryKey: CHAVE_INICIO,
    queryFn: ({ signal }) => api.get<InicioDto>('/home', signal),
  });
}
