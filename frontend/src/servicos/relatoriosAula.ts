import { useQuery } from '@tanstack/react-query';
import { api, type Esquemas } from './clienteApi';

export type RelatorioAulaDto = Esquemas['LessonReportDto'];
export type TrabalhoRefDto = Esquemas['JobRefDto'];
export type AprovacaoDto = Esquemas['ApprovalDto'];

/** Áudio pronto para envio: gravação do navegador ou arquivo escolhido. */
export interface AudioSelecionado {
  arquivo: Blob;
  nome: string;
}

export const chaveRelatorioAula = (id: string | undefined) => ['relatorio-aula', id] as const;

function formularioAudio(audio: AudioSelecionado, extras: Record<string, string> = {}): FormData {
  const formulario = new FormData();
  formulario.append('audio', audio.arquivo, audio.nome);
  for (const [chave, valor] of Object.entries(extras)) {
    formulario.append(chave, valor);
  }
  return formulario;
}

/** Estado e conteúdo vigente do relatório de aula. */
export function useRelatorioAula(id: string | undefined) {
  return useQuery<RelatorioAulaDto>({
    queryKey: chaveRelatorioAula(id),
    queryFn: ({ signal }) => api.get<RelatorioAulaDto>(`/lesson-reports/${id}`, signal),
    enabled: Boolean(id),
  });
}

export const relatoriosAula = {
  obter: (id: string) => api.get<RelatorioAulaDto>(`/lesson-reports/${id}`),
  registrar: (alunoId: string, audio: AudioSelecionado) =>
    api.postFormulario<TrabalhoRefDto>(`/students/${alunoId}/lesson-reports`, formularioAudio(audio)),
  responderTexto: (id: string, texto: string) =>
    api.post<TrabalhoRefDto>(`/lesson-reports/${id}/answers`, { text: texto }),
  responderAudio: (id: string, audio: AudioSelecionado) =>
    api.postFormulario<TrabalhoRefDto>(`/lesson-reports/${id}/answers`, formularioAudio(audio)),
  alterarTexto: (id: string, texto: string, versaoBase: number) =>
    api.post<TrabalhoRefDto>(`/lesson-reports/${id}/changes`, { text: texto, baseVersion: versaoBase }),
  alterarAudio: (id: string, audio: AudioSelecionado, versaoBase: number) =>
    api.postFormulario<TrabalhoRefDto>(
      `/lesson-reports/${id}/changes`,
      formularioAudio(audio, { baseVersion: String(versaoBase) }),
    ),
  novaTentativa: (id: string) => api.post<TrabalhoRefDto>(`/lesson-reports/${id}/retry`),
  aprovar: (id: string, versaoConfirmada: number) =>
    api.post<AprovacaoDto>(`/lesson-reports/${id}/approval`, { confirmedVersion: versaoConfirmada }),
  cancelar: (id: string) => api.post<void>(`/lesson-reports/${id}/cancellation`),
};

/** Caminho do PDF de conferência de uma versão (entregue por rota autorizada, R-11). */
export function caminhoPdfConferencia(id: string, versao: number): string {
  return `/lesson-reports/${id}/versions/${versao}/pdf`;
}
