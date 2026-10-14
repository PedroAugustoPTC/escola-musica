import catalogo from './mensagens.json';

/** Códigos do catálogo único de mensagens (contracts/mensagens.md). */
export type CodigoMensagem = keyof typeof catalogo;

export type Parametros = Record<string, string | number | null | undefined>;

/** Texto do catálogo com os placeholders {nome} substituídos (ausentes ficam vazios). */
export function msg(codigo: CodigoMensagem | string, parametros: Parametros = {}): string {
  const modelo = (catalogo as Record<string, string>)[codigo];
  if (modelo === undefined) {
    return (catalogo as Record<string, string>).INTERNAL_ERROR ?? '';
  }
  return modelo.replace(/\{([A-Za-z]+)\}/g, (_, chave: string) => {
    const valor = parametros[chave];
    return valor === null || valor === undefined ? '' : String(valor);
  });
}

export function existeMensagem(codigo: string): boolean {
  return Object.prototype.hasOwnProperty.call(catalogo, codigo);
}
