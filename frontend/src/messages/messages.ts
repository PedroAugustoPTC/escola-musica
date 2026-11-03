import catalog from './messages.json';

/** Códigos do catálogo único de mensagens (contracts/mensagens.md). */
export type MessageCode = keyof typeof catalog;

export type MessageParams = Record<string, string | number | null | undefined>;

/** Texto do catálogo com os placeholders {nome} substituídos (ausentes ficam vazios). */
export function msg(code: MessageCode | string, params: MessageParams = {}): string {
  const model = (catalog as Record<string, string>)[code];
  // código fora do catálogo cai em INTERNAL_ERROR, que também passa pelo preenchimento (nunca "{reference}" cru)
  return renderMessage(model ?? (catalog as Record<string, string>).INTERNAL_ERROR ?? '', params);
}

/**
 * Preenche o modelo. Sem o parâmetro que a frase exige (hoje só o `{reference}` da falha interna), a frase
 * inteira sai do texto: nunca aparece "{reference}" literal nem uma lacuna entre as palavras, termine ou não
 * a frase em pontuação, esteja ela no começo, no meio ou no fim do texto.
 */
export function renderMessage(model: string, params: MessageParams = {}): string {
  const text = dropSentencesWithMissingReference(model, params);
  return text.replace(/\{([A-Za-z]+)\}/g, (_, key: string) => {
    const value = params[key];
    return value === null || value === undefined ? '' : String(value);
  });
}

// Uma frase vai até a pontuação final (ou até o fim do texto, se não houver) e leva os espaços que a seguem.
const SENTENCE = /[^.!?]+(?:[.!?]+|$)\s*/g;
const REFERENCE_PLACEHOLDER = '{reference}';

function dropSentencesWithMissingReference(model: string, params: MessageParams): string {
  const reference = params.reference;
  if (reference !== null && reference !== undefined && reference !== '') return model;
  if (!model.includes(REFERENCE_PLACEHOLDER)) return model;
  const kept = (model.match(SENTENCE) ?? [])
    .filter((sentence) => !sentence.includes(REFERENCE_PLACEHOLDER))
    .join('')
    .trim();
  // texto que só existe em torno da referência: melhor o que sobra dele do que uma mensagem vazia
  return kept !== '' ? kept : model.split(REFERENCE_PLACEHOLDER).join('').replace(/\s+/g, ' ').trim();
}

export function hasMessage(code: string): boolean {
  return Object.prototype.hasOwnProperty.call(catalog, code);
}
