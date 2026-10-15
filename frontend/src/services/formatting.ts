/** Fuso da escola: carimbos de tempo são mostrados nele (FR-016, rotas-portal.md). */
export const SCHOOL_TIMEZONE = 'America/Sao_Paulo';

/** `AAAA-MM-DD` → `dd/mm/aaaa`, sem conversão de fuso (lição 3 do MVP). */
export function formatDate(data: string | null | undefined): string {
  if (!data) return '';
  const [year, month, day] = data.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

const DATE_FORMATTER = new Intl.DateTimeFormat('pt-BR', {
  timeZone: SCHOOL_TIMEZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

const TIME_FORMATTER = new Intl.DateTimeFormat('pt-BR', {
  timeZone: SCHOOL_TIMEZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** Instante ISO → `dd/mm/aaaa` no fuso da escola. */
export function instantDate(instant: string): string {
  return DATE_FORMATTER.format(new Date(instant));
}

/** Instante ISO → `hh:mm` no fuso da escola. */
export function instantTime(instant: string): string {
  return TIME_FORMATTER.format(new Date(instant));
}

/** Hoje (data civil) no fuso da escola, como `AAAA-MM-DD`. */
export function todayAtSchool(now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: SCHOOL_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
  return parts;
}

export function firstName(name: string): string {
  return name.trim().split(/\s+/)[0] ?? name;
}
