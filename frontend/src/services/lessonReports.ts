import { useQuery } from '@tanstack/react-query';
import { api, type Schemas } from './apiClient';

export type LessonReportDto = Schemas['LessonReportDto'];
export type JobRefDto = Schemas['JobRefDto'];
export type ApprovalDto = Schemas['ApprovalDto'];

/** Áudio pronto para envio: gravação do navegador ou arquivo escolhido. */
export interface SelectedAudio {
  file: Blob;
  name: string;
}

export const lessonReportKey = (id: string | undefined) => ['lesson-report', id] as const;

function audioForm(audio: SelectedAudio, extras: Record<string, string> = {}): FormData {
  const form = new FormData();
  form.append('audio', audio.file, audio.name);
  for (const [key, value] of Object.entries(extras)) {
    form.append(key, value);
  }
  return form;
}

/** Estado e conteúdo vigente do relatório de aula. */
export function useLessonReport(id: string | undefined) {
  return useQuery<LessonReportDto>({
    queryKey: lessonReportKey(id),
    queryFn: ({ signal }) => api.get<LessonReportDto>(`/lesson-reports/${id}`, signal),
    enabled: Boolean(id),
  });
}

export const lessonReports = {
  get: (id: string) => api.get<LessonReportDto>(`/lesson-reports/${id}`),
  register: (studentId: string, audio: SelectedAudio) =>
    api.postForm<JobRefDto>(`/students/${studentId}/lesson-reports`, audioForm(audio)),
  answerText: (id: string, text: string) => api.post<JobRefDto>(`/lesson-reports/${id}/answers`, { text }),
  answerAudio: (id: string, audio: SelectedAudio) =>
    api.postForm<JobRefDto>(`/lesson-reports/${id}/answers`, audioForm(audio)),
  changeText: (id: string, text: string, baseVersion: number) =>
    api.post<JobRefDto>(`/lesson-reports/${id}/changes`, { text, baseVersion }),
  changeAudio: (id: string, audio: SelectedAudio, baseVersion: number) =>
    api.postForm<JobRefDto>(
      `/lesson-reports/${id}/changes`,
      audioForm(audio, { baseVersion: String(baseVersion) }),
    ),
  newAttempt: (id: string) => api.post<JobRefDto>(`/lesson-reports/${id}/retry`),
  approve: (id: string, confirmedVersion: number) =>
    api.post<ApprovalDto>(`/lesson-reports/${id}/approval`, { confirmedVersion }),
  cancel: (id: string) => api.post<void>(`/lesson-reports/${id}/cancellation`),
};

/** Caminho do PDF de conferência de uma versão (entregue por rota autorizada, R-11). */
export function reviewPdfPath(id: string, version: number): string {
  return `/lesson-reports/${id}/versions/${version}/pdf`;
}
