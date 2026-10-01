import { useQuery } from '@tanstack/react-query';
import { api, type Esquemas } from './clienteApi';

export type RelatorioAulaDto = Esquemas['RelatorioAulaDto'];
export type TrabalhoRefDto = Esquemas['TrabalhoRefDto'];
export type AprovacaoDto = Esquemas['AprovacaoDto'];

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
    queryFn: ({ signal }) => api.get<RelatorioAulaDto>(`/relatorios-aula/${id}`, signal),
    enabled: Boolean(id),
  });
}

export const relatoriosAula = {
  obter: (id: string) => api.get<RelatorioAulaDto>(`/relatorios-aula/${id}`),
  registrar: (alunoId: string, audio: AudioSelecionado) =>
    api.postFormulario<TrabalhoRefDto>(`/alunos/${alunoId}/relatorios-aula`, formularioAudio(audio)),
  responderTexto: (id: string, texto: string) =>
    api.post<TrabalhoRefDto>(`/relatorios-aula/${id}/respostas`, { texto }),
  responderAudio: (id: string, audio: AudioSelecionado) =>
    api.postFormulario<TrabalhoRefDto>(`/relatorios-aula/${id}/respostas`, formularioAudio(audio)),
  alterarTexto: (id: string, texto: string, versaoBase: number) =>
    api.post<TrabalhoRefDto>(`/relatorios-aula/${id}/alteracoes`, { texto, versaoBase }),
  alterarAudio: (id: string, audio: AudioSelecionado, versaoBase: number) =>
    api.postFormulario<TrabalhoRefDto>(
      `/relatorios-aula/${id}/alteracoes`,
      formularioAudio(audio, { versaoBase: String(versaoBase) }),
    ),
  novaTentativa: (id: string) => api.post<TrabalhoRefDto>(`/relatorios-aula/${id}/nova-tentativa`),
  aprovar: (id: string, versaoConfirmada: number) =>
    api.post<AprovacaoDto>(`/relatorios-aula/${id}/aprovacao`, { versaoConfirmada }),
  cancelar: (id: string) => api.post<void>(`/relatorios-aula/${id}/cancelamento`),
};

/** Caminho do PDF de conferência de uma versão (entregue por rota autorizada, R-11). */
export function caminhoPdfConferencia(id: string, versao: number): string {
  return `/relatorios-aula/${id}/versoes/${versao}/pdf`;
}
