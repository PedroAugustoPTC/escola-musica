import { useQuery } from '@tanstack/react-query';
import { msg } from '../messages/messages';
import { api, type Schemas } from './apiClient';
import { formatDate } from './formatting';
import type { SelectedAudio, ApprovalDto, JobRefDto } from './lessonReports';

export type SemesterDto = Schemas['SemesterDto'];
export type SemesterPreviewDto = Schemas['SemesterPreviewDto'];
export type SemesterReportDto = Schemas['SemesterReportDto'];

export const semesterReportKey = (id: string | undefined) => ['semester-report', id] as const;

export function useSemesters(studentId: string | undefined) {
  return useQuery<SemesterDto[]>({
    queryKey: ['semesters', studentId],
    queryFn: ({ signal }) => api.get<SemesterDto[]>(`/students/${studentId}/semesters`, signal),
    enabled: Boolean(studentId),
  });
}

export function useSemesterPreview(studentId: string | undefined, periodKey: string | null) {
  return useQuery<SemesterPreviewDto>({
    queryKey: ['semester-preview', studentId, periodKey],
    queryFn: ({ signal }) =>
      api.get<SemesterPreviewDto>(`/students/${studentId}/semesters/${periodKey}/preview`, signal),
    enabled: Boolean(studentId && periodKey),
    gcTime: 0,
  });
}

export function useSemesterReport(id: string | undefined) {
  return useQuery<SemesterReportDto>({
    queryKey: semesterReportKey(id),
    queryFn: ({ signal }) => api.get<SemesterReportDto>(`/semester-reports/${id}`, signal),
    enabled: Boolean(id),
  });
}

function audioForm(audio: SelectedAudio, baseVersion: number): FormData {
  const form = new FormData();
  form.append('audio', audio.file, audio.name);
  form.append('baseVersion', String(baseVersion));
  return form;
}

export const semester = {
  get: (id: string) => api.get<SemesterReportDto>(`/semester-reports/${id}`),
  generate: (studentId: string, periodKey: string, confirmDespiteOpenReports: boolean) =>
    api.post<JobRefDto>(`/students/${studentId}/semester-reports`, {
      periodKey,
      confirmDespiteOpenReports,
    }),
  changeText: (id: string, text: string, baseVersion: number) =>
    api.post<JobRefDto>(`/semester-reports/${id}/changes`, { text, baseVersion }),
  changeAudio: (id: string, audio: SelectedAudio, baseVersion: number) =>
    api.postForm<JobRefDto>(`/semester-reports/${id}/changes`, audioForm(audio, baseVersion)),
  approve: (id: string, confirmedVersion: number) =>
    api.post<ApprovalDto>(`/semester-reports/${id}/approval`, { confirmedVersion }),
  cancel: (id: string) => api.post<void>(`/semester-reports/${id}/cancellation`),
  discardConsolidation: (jobId: string) => api.post<void>(`/jobs/${jobId}/discard`),
};

export function semesterPdfPath(id: string, version: number): string {
  return `/semester-reports/${id}/versions/${version}/pdf`;
}

/** Campo de uma seção do semestral: itens em lista ou texto (vazio mostra "—"). */
export interface SemesterField {
  label: string | null;
  items: string[];
  text: string;
}

export interface SemesterSection {
  number: number;
  title: string;
  fields: SemesterField[];
}

type Content = SemesterReportDto['content'];
type Info = SemesterReportDto['generalInformation'];
type JsonObject = Record<string, unknown>;

function text(label: string, value: unknown): SemesterField {
  return { label: msg(label), items: [], text: typeof value === 'string' ? value : '' };
}

function list(label: string | null, value: unknown): SemesterField {
  return {
    label: label ? msg(label) : null,
    items: Array.isArray(value) ? value.filter((i): i is string => typeof i === 'string') : [],
    text: '',
  };
}

const part = (content: Content, key: string): JsonObject =>
  ((content as unknown as JsonObject)[key] as JsonObject | undefined) ?? {};

/** As 10 seções do semestral (data-model.md §9) com os rótulos do catálogo único. */
export function semesterSections(info: Info, content: Content): SemesterSection[] {
  const f = part(content, 'attendanceAndStudy');
  const t = part(content, 'technique');
  const m = part(content, 'musicality');
  const l = part(content, 'readingAndMemorization');
  const a = part(content, 'familySupport');
  const p = part(content, 'nextSemesterPlan');
  return [
    {
      number: 1,
      title: msg('GENERAL_INFORMATION_SECTION'),
      fields: [
        text('INFO_NAME', info.name),
        text('INFO_AGE', msg('AGE_VALUE_INFO', { age: info.age })),
        text('INFO_INSTRUMENT', info.instrument ?? ''),
        text('INFO_TEACHER', info.teacher),
        text(
          'INFO_PERIOD',
          `${info.periodLabel} (${formatDate(info.periodStart)} a ${formatDate(info.periodEnd)})`,
        ),
        text('CONSIDERED_LESSONS_INFO', String(info.consideredLessonCount)),
      ],
    },
    {
      number: 2,
      title: msg('ATTENDANCE_AND_STUDY_SECTION'),
      fields: [
        text('LESSON_ATTENDANCE_FIELD', f.lessonAttendance),
        text('HOME_STUDY_REGULARITY_FIELD', f.homeStudyRegularity),
        text('ENGAGEMENT_FIELD', f.engagement),
        text('GENERAL_COMMENT_FIELD', f.generalComment),
      ],
    },
    {
      number: 3,
      title: msg('TECHNIQUE_SECTION'),
      fields: [
        list('WORKED_TOPICS_FIELD', t.workedTopics),
        text('TECHNICAL_PROGRESS_FIELD', t.technicalProgress),
        list('POINTS_TO_DEVELOP_FIELD', t.pointsToDevelop),
        list('PERFORMED_STUDIES_FIELD', t.completedStudies),
      ],
    },
    {
      number: 4,
      title: msg('MUSICALITY_SECTION'),
      fields: [
        list('STUDIED_REPERTOIRE_FIELD', m.studiedRepertoire),
        text('SOUND_FIELD', m.sound),
        text('RHYTHM_FIELD', m.rhythm),
        text('PHRASING_FIELD', m.phrasing),
        text('ARTICULATION_FIELD', m.articulation),
        text('STYLE_FIELD', m.style),
        text('MUSICAL_COMMENT_FIELD', m.musicalComment),
      ],
    },
    {
      number: 5,
      title: msg('READING_AND_MEMORIZATION_SECTION'),
      fields: [text('ASSESSMENT_FIELD', l.assessment), text('COMMENT_FIELD', l.comment)],
    },
    {
      number: 6,
      title: msg('ATTENTION_POINTS_SECTION'),
      fields: [list(null, part(content, 'attentionPoints').items)],
    },
    {
      number: 7,
      title: msg('PEDAGOGICAL_STRATEGIES_SECTION'),
      fields: [list(null, part(content, 'pedagogicalStrategies').items)],
    },
    {
      number: 8,
      title: msg('FAMILY_SUPPORT_SECTION'),
      fields: [
        text('PRESENCE_LEVEL_FIELD', a.presenceLevel),
        text('TEACHER_OBSERVATION_FIELD', a.teacherObservation),
      ],
    },
    {
      number: 9,
      title: msg('NEXT_SEMESTER_PLAN_SECTION'),
      fields: [
        list('TECHNICAL_GOALS_FIELD', p.technicalGoals),
        list('MUSICAL_GOALS_FIELD', p.musicalGoals),
        list('STUDY_HABITS_FIELD', p.studyHabits),
        list('PROPOSED_REPERTOIRE_FIELD', p.proposedRepertoire),
        list('TECHNICAL_STUDIES_FIELD', p.technicalStudies),
        list('READING_AND_THEORY_FIELD', p.readingAndTheory),
        text('SEMESTER_PROJECT_FIELD', p.semesterProject),
      ],
    },
    {
      number: 10,
      title: msg('FINAL_OPINION_SECTION'),
      fields: [
        {
          label: null,
          items: [],
          text: typeof content.finalOpinion === 'string' ? content.finalOpinion : '',
        },
      ],
    },
  ];
}
