import catalog from './messages.json';

/** Códigos do catálogo único de mensagens (contracts/mensagens.md). */
export type MessageCode = keyof typeof catalog;

export type MessageParams = Record<string, string | number | null | undefined>;

/** Texto do catálogo com os placeholders {nome} substituídos (ausentes ficam vazios). */
export function msg(code: MessageCode | string, params: MessageParams = {}): string {
  const model = (catalog as Record<string, string>)[code];
  if (model === undefined) {
    return (catalog as Record<string, string>).INTERNAL_ERROR ?? '';
  }
  return model.replace(/\{([A-Za-z]+)\}/g, (_, key: string) => {
    const value = params[key];
    return value === null || value === undefined ? '' : String(value);
  });
}

export function hasMessage(code: string): boolean {
  return Object.prototype.hasOwnProperty.call(catalog, code);
}
