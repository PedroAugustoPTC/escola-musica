/** Fuso da escola: carimbos de tempo são mostrados nele (FR-016, rotas-portal.md). */
export const FUSO_ESCOLA = 'America/Sao_Paulo';

/** `AAAA-MM-DD` → `dd/mm/aaaa`, sem conversão de fuso (lição 3 do MVP). */
export function formatarData(data: string | null | undefined): string {
  if (!data) return '';
  const [ano, mes, dia] = data.slice(0, 10).split('-');
  return `${dia}/${mes}/${ano}`;
}

const DATA_FUSO = new Intl.DateTimeFormat('pt-BR', {
  timeZone: FUSO_ESCOLA,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

const HORA_FUSO = new Intl.DateTimeFormat('pt-BR', {
  timeZone: FUSO_ESCOLA,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** Instante ISO → `dd/mm/aaaa` no fuso da escola. */
export function dataDoInstante(instante: string): string {
  return DATA_FUSO.format(new Date(instante));
}

/** Instante ISO → `hh:mm` no fuso da escola. */
export function horaDoInstante(instante: string): string {
  return HORA_FUSO.format(new Date(instante));
}

/** Hoje (data civil) no fuso da escola, como `AAAA-MM-DD`. */
export function hojeNaEscola(agora: Date = new Date()): string {
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone: FUSO_ESCOLA,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(agora);
  return partes;
}

export function primeiroNome(nome: string): string {
  return nome.trim().split(/\s+/)[0] ?? nome;
}
