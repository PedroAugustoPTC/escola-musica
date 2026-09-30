import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, type Esquemas } from './clienteApi';

export type AlunoResumoDto = Esquemas['AlunoResumoDto'];
export type BuscaAlunosDto = Esquemas['BuscaAlunosDto'];

/** Alunos associados com busca aproximada (FR-013). */
export function useAlunos(busca: string) {
  const termo = busca.trim();
  return useQuery<BuscaAlunosDto>({
    queryKey: ['alunos', termo],
    queryFn: ({ signal }) =>
      api.get<BuscaAlunosDto>(termo ? `/alunos?busca=${encodeURIComponent(termo)}` : '/alunos', signal),
    placeholderData: keepPreviousData,
  });
}

/** Resumo de um aluno associado (403 se não for do professor). */
export function useAluno(alunoId: string | undefined) {
  return useQuery<AlunoResumoDto>({
    queryKey: ['aluno', alunoId],
    queryFn: ({ signal }) => api.get<AlunoResumoDto>(`/alunos/${alunoId}`, signal),
    enabled: Boolean(alunoId),
  });
}
