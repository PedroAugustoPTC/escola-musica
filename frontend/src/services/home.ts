import { useQuery } from '@tanstack/react-query';
import { api, type Schemas } from './apiClient';

export type HomeDto = Schemas['HomeDto'];
export type OpenReportDto = Schemas['OpenReportDto'];

export const HOME_KEY = ['home'] as const;

/** Dados do Painel (FR-012, FR-035). */
export function useHome() {
  return useQuery<HomeDto>({
    queryKey: HOME_KEY,
    queryFn: ({ signal }) => api.get<HomeDto>('/home', signal),
  });
}
