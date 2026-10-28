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
  // Sem o parâmetro que a frase exige (hoje só o `{reference}` da falha interna), a frase inteira sai do texto:
  // nunca aparece "{reference}" literal nem uma lacuna entre as palavras.
  const text = dropSentencesWithMissingParam(model, params);
  return text.replace(/\{([A-Za-z]+)\}/g, (_, key: string) => {
    const value = params[key];
    return value === null || value === undefined ? '' : String(value);
  });
}

const REFERENCE_SENTENCE = /\s*[^.!?]*\{reference\}[^.!?]*[.!?]/;

function dropSentencesWithMissingParam(model: string, params: MessageParams): string {
  const reference = params.reference;
  if (reference !== null && reference !== undefined && reference !== '') return model;
  return model.replace(REFERENCE_SENTENCE, '').trim();
}

export function hasMessage(code: string): boolean {
  return Object.prototype.hasOwnProperty.call(catalog, code);
}
