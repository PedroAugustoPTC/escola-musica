/**
 * Termo de busca com forma de CPF (11 dígitos, com ou sem pontos, traço e espaços).
 * Só a forma: a busca por CPF não existe, então o termo é recusado antes de ir à URL, à API ou ao cache.
 */
export function looksLikeCpf(term: string): boolean {
  return /^\d{11}$/.test(term.replace(/[.\-\s]/g, ''));
}
