const SCHOOL_TIME_ZONE = 'America/Sao_Paulo';

const dateFormat = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: SCHOOL_TIME_ZONE,
});

/** "01/09/2026" no fuso da escola. */
export function formatSchoolDate(instant: string): string {
  return dateFormat.format(new Date(instant));
}
