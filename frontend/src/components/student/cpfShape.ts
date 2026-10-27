/**
 * Termo de busca com forma de CPF (11 dígitos, com ou sem pontos, traço e espaços).
 * Só a forma: a busca por CPF não existe, então o termo é recusado antes de ir à URL, à API ou ao cache.
 */
export function looksLikeCpf(term: string): boolean {
  return /^\d{11}$/.test(term.replace(/[.\-\s]/g, ''));
}

/**
 * Termo de busca que pode ir à chave de consulta, à URL e à API: o termo aparado, ou vazio quando tem forma
 * de CPF (nunca existe busca por CPF, então o número jamais sai da tela).
 */
export function searchTermOf(term: string): string {
  const trimmed = term.trim();
  return looksLikeCpf(trimmed) ? '' : trimmed;
}
