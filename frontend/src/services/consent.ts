import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './apiClient';
import { MY_STUDENTS_KEY } from './myStudents';
import type { components } from './privacy-api-types';

type Schemas = components['schemas'];

export type ConsentInput = Schemas['ConsentInput'];
export type ConsentTermSummary = Schemas['ConsentTermSummary'];

/** Resposta do registro avulso: nunca devolve o nome de quem consentiu. */
export interface ConsentRecorded {
  status: 'VALID';
  termVersion: number;
  consentedOn: string;
}

export const CONSENT_TERM_KEY = ['consent-term'] as const;

/** Versão vigente do termo e contato da LGPD, para o formulário do menor (CONSENT_TERM_READ). */
export function useCurrentConsentTerm() {
  return useQuery<ConsentTermSummary>({
    queryKey: CONSENT_TERM_KEY,
    queryFn: ({ signal }) => api.get<ConsentTermSummary>('/my-students/consent-term', signal),
    retry: false,
  });
}

/** Registra o consentimento de um aluno do professor (a partir do aviso de bloqueio). `gcTime: 0`: o corpo leva o nome. */
export function useRecordOwnConsent(studentId: string) {
  const client = useQueryClient();
  return useMutation<ConsentRecorded, Error, ConsentInput>({
    mutationFn: (body) => api.post<ConsentRecorded>(`/my-students/${studentId}/consents`, body),
    gcTime: 0,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: MY_STUDENTS_KEY });
    },
  });
}
