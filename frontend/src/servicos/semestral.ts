import { useQuery } from '@tanstack/react-query';
import { msg } from '../mensagens/mensagens';
import { api, type Esquemas } from './clienteApi';
import { formatarData } from './formatacao';
import type { AudioSelecionado, AprovacaoDto, TrabalhoRefDto } from './relatoriosAula';

export type SemestreDto = Esquemas['SemesterDto'];
export type PreviaSemestralDto = Esquemas['SemesterPreviewDto'];
export type RelatorioSemestralDto = Esquemas['SemesterReportDto'];

export const chaveRelatorioSemestral = (id: string | undefined) => ['relatorio-semestral', id] as const;

export function useSemestres(alunoId: string | undefined) {
  return useQuery<SemestreDto[]>({
    queryKey: ['semestres', alunoId],
    queryFn: ({ signal }) => api.get<SemestreDto[]>(`/students/${alunoId}/semesters`, signal),
    enabled: Boolean(alunoId),
  });
}

export function usePreviaSemestral(alunoId: string | undefined, periodoChave: string | null) {
  return useQuery<PreviaSemestralDto>({
    queryKey: ['previa-semestral', alunoId, periodoChave],
    queryFn: ({ signal }) =>
      api.get<PreviaSemestralDto>(`/students/${alunoId}/semesters/${periodoChave}/preview`, signal),
    enabled: Boolean(alunoId && periodoChave),
    gcTime: 0,
  });
}

export function useRelatorioSemestral(id: string | undefined) {
  return useQuery<RelatorioSemestralDto>({
    queryKey: chaveRelatorioSemestral(id),
    queryFn: ({ signal }) => api.get<RelatorioSemestralDto>(`/semester-reports/${id}`, signal),
    enabled: Boolean(id),
  });
}

function formularioAudio(audio: AudioSelecionado, versaoBase: number): FormData {
  const formulario = new FormData();
  formulario.append('audio', audio.arquivo, audio.nome);
  formulario.append('baseVersion', String(versaoBase));
  return formulario;
}

export const semestral = {
  obter: (id: string) => api.get<RelatorioSemestralDto>(`/semester-reports/${id}`),
  gerar: (alunoId: string, periodoChave: string, confirmarMesmoComEmAberto: boolean) =>
    api.post<TrabalhoRefDto>(`/students/${alunoId}/semester-reports`, {
      periodKey: periodoChave,
      confirmDespiteOpenReports: confirmarMesmoComEmAberto,
    }),
  alterarTexto: (id: string, texto: string, versaoBase: number) =>
    api.post<TrabalhoRefDto>(`/semester-reports/${id}/changes`, { text: texto, baseVersion: versaoBase }),
  alterarAudio: (id: string, audio: AudioSelecionado, versaoBase: number) =>
    api.postFormulario<TrabalhoRefDto>(
      `/semester-reports/${id}/changes`,
      formularioAudio(audio, versaoBase),
    ),
  aprovar: (id: string, versaoConfirmada: number) =>
    api.post<AprovacaoDto>(`/semester-reports/${id}/approval`, { confirmedVersion: versaoConfirmada }),
  cancelar: (id: string) => api.post<void>(`/semester-reports/${id}/cancellation`),
  descartarConsolidacao: (trabalhoId: string) => api.post<void>(`/jobs/${trabalhoId}/discard`),
};

export function caminhoPdfSemestral(id: string, versao: number): string {
  return `/semester-reports/${id}/versions/${versao}/pdf`;
}

/** Campo de uma seção do semestral: itens em lista ou texto (vazio mostra "—"). */
export interface CampoSemestral {
  rotulo: string | null;
  itens: string[];
  texto: string;
}

export interface SecaoSemestral {
  numero: number;
  titulo: string;
  campos: CampoSemestral[];
}

type Conteudo = RelatorioSemestralDto['content'];
type Info = RelatorioSemestralDto['generalInformation'];
type Objeto = Record<string, unknown>;

function texto(rotulo: string, valor: unknown): CampoSemestral {
  return { rotulo: msg(rotulo), itens: [], texto: typeof valor === 'string' ? valor : '' };
}

function lista(rotulo: string | null, valor: unknown): CampoSemestral {
  return {
    rotulo: rotulo ? msg(rotulo) : null,
    itens: Array.isArray(valor) ? valor.filter((i): i is string => typeof i === 'string') : [],
    texto: '',
  };
}

const parte = (conteudo: Conteudo, chave: string): Objeto =>
  ((conteudo as unknown as Objeto)[chave] as Objeto | undefined) ?? {};

/** As 10 seções do semestral (data-model.md §9) com os rótulos do catálogo único. */
export function secoesSemestral(info: Info, conteudo: Conteudo): SecaoSemestral[] {
  const f = parte(conteudo, 'frequenciaEEstudo');
  const t = parte(conteudo, 'tecnica');
  const m = parte(conteudo, 'musicalidade');
  const l = parte(conteudo, 'leituraEMemorizacao');
  const a = parte(conteudo, 'acompanhamentoFamiliar');
  const p = parte(conteudo, 'planejamentoProximoSemestre');
  return [
    {
      numero: 1,
      titulo: msg('GENERAL_INFORMATION_SECTION'),
      campos: [
        texto('INFO_NAME', info.name),
        texto('INFO_AGE', msg('AGE_VALUE_INFO', { age: info.age })),
        texto('INFO_INSTRUMENT', info.instrument ?? ''),
        texto('INFO_TEACHER', info.teacher),
        texto(
          'INFO_PERIOD',
          `${info.periodLabel} (${formatarData(info.periodStart)} a ${formatarData(info.periodEnd)})`,
        ),
        texto('CONSIDERED_LESSONS_INFO', String(info.consideredLessonCount)),
      ],
    },
    {
      numero: 2,
      titulo: msg('ATTENDANCE_AND_STUDY_SECTION'),
      campos: [
        texto('LESSON_ATTENDANCE_FIELD', f.frequenciaAulas),
        texto('HOME_STUDY_REGULARITY_FIELD', f.regularidadeEstudoEmCasa),
        texto('ENGAGEMENT_FIELD', f.engajamento),
        texto('GENERAL_COMMENT_FIELD', f.comentarioGeral),
      ],
    },
    {
      numero: 3,
      titulo: msg('TECHNIQUE_SECTION'),
      campos: [
        lista('WORKED_TOPICS_FIELD', t.workedTopics),
        texto('TECHNICAL_PROGRESS_FIELD', t.evolucaoTecnica),
        lista('POINTS_TO_DEVELOP_FIELD', t.pontosADesenvolver),
        lista('PERFORMED_STUDIES_FIELD', t.estudosRealizados),
      ],
    },
    {
      numero: 4,
      titulo: msg('MUSICALITY_SECTION'),
      campos: [
        lista('STUDIED_REPERTOIRE_FIELD', m.repertorioEstudado),
        texto('SOUND_FIELD', m.som),
        texto('RHYTHM_FIELD', m.ritmo),
        texto('PHRASING_FIELD', m.fraseado),
        texto('ARTICULATION_FIELD', m.articulacao),
        texto('STYLE_FIELD', m.estilo),
        texto('MUSICAL_COMMENT_FIELD', m.comentarioMusical),
      ],
    },
    {
      numero: 5,
      titulo: msg('READING_AND_MEMORIZATION_SECTION'),
      campos: [texto('ASSESSMENT_FIELD', l.avaliacao), texto('COMMENT_FIELD', l.comentario)],
    },
    {
      numero: 6,
      titulo: msg('ATTENTION_POINTS_SECTION'),
      campos: [lista(null, parte(conteudo, 'pontosDeAtencao').items)],
    },
    {
      numero: 7,
      titulo: msg('PEDAGOGICAL_STRATEGIES_SECTION'),
      campos: [lista(null, parte(conteudo, 'estrategiasPedagogicas').items)],
    },
    {
      numero: 8,
      titulo: msg('FAMILY_SUPPORT_SECTION'),
      campos: [
        texto('PRESENCE_LEVEL_FIELD', a.nivelDePresenca),
        texto('TEACHER_OBSERVATION_FIELD', a.observacaoDoProfessor),
      ],
    },
    {
      numero: 9,
      titulo: msg('NEXT_SEMESTER_PLAN_SECTION'),
      campos: [
        lista('TECHNICAL_GOALS_FIELD', p.metasTecnicas),
        lista('MUSICAL_GOALS_FIELD', p.metasMusicais),
        lista('STUDY_HABITS_FIELD', p.habitosDeEstudo),
        lista('PROPOSED_REPERTOIRE_FIELD', p.repertorioProposto),
        lista('TECHNICAL_STUDIES_FIELD', p.estudosTecnicos),
        lista('READING_AND_THEORY_FIELD', p.leituraETeoria),
        texto('SEMESTER_PROJECT_FIELD', p.projetoDoSemestre),
      ],
    },
    {
      numero: 10,
      titulo: msg('FINAL_OPINION_SECTION'),
      campos: [
        {
          rotulo: null,
          itens: [],
          texto: typeof conteudo.finalOpinion === 'string' ? conteudo.finalOpinion : '',
        },
      ],
    },
  ];
}
