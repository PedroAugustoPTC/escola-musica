// Alinhamento token a token de dois arquivos que não são Java, antes e depois da renomeação (T054, T057).
//
//   const result = alignSimple(kind, textoBase, textoTrabalho, { map, file });
//
// Mesmo contrato do `alignJava`: exige o mesmo número de tokens e classifica cada diferença:
//   ident ↔ ident        o par (antigo → novo) DEVE estar no mapa (ou ser composto de pares do mapa, para chaves
//                         pontilhadas e com hífen: `fuso-horario`, `relatorios.escola`);
//   key (chave JSON)     idem, entre aspas;
//   string               idêntica byte a byte; de uma palavra de identificador, pode ser par do mapa; código embutido
//                         (consulta SQL, atribuição `chave=valor`, expressão `${…}`) confere palavra a palavra pelo mapa;
//   text, number, op     idênticos (texto de HTML/Markdown é conteúdo para pessoas);
// Exceções ficam em `explicit-pairs.json` → `literals`/`tokenEdits`, com motivo.
import { isSingleWord } from './java-lexer.mjs';
import { isIdentifierWord, lexSimple, unquote } from './simple-lexers.mjs';

const WORD = /[A-Za-z_][A-Za-z0-9_]*/g;

/** Aplica o mapa a cada palavra de identificador do texto (código embutido). */
export function mapEmbedded(text, map) {
  return text.replace(WORD, (word) => map.mapIdent(word));
}

/** Código embutido em literal: SQL/JPQL, atribuição de propriedade, expressão de modelo. */
export function looksLikeCode(text) {
  return (
    /^\s*(select|insert|update|delete|with|alter|create|drop|truncate|from|set|comment|do)\b/i.test(text) ||
    /^[A-Za-z_][\w.-]*=/.test(text) ||
    /\$\{[^}]*\}/.test(text) ||
    /^[A-Za-z_][\w-]*(\.[A-Za-z_][\w-]*)+$/.test(text)
  );
}

/** Par de identificadores composto: mesmos separadores, cada parte igual ou par do mapa. */
/** Valor de esquema que cita propriedade: `"nome"` em `required` ou `#/$defs/nome` em `$ref`. */
function schemaRefPair(from, to, map) {
  if (from.startsWith('#/')) {
    const cut = from.lastIndexOf('/') + 1;
    return to.startsWith(from.slice(0, cut)) && compositePair(from.slice(cut), to.slice(cut), map);
  }
  return compositePair(from, to, map);
}

export function compositePair(antigo, novo, map) {
  if (map.isValidPair(antigo, novo)) return true;
  const partsOf = (text) => text.split(/([._-])/);
  const a = partsOf(antigo);
  const b = partsOf(novo);
  if (a.length !== b.length || a.length === 1) return false;
  return a.every((part, index) => part === b[index] || (index % 2 === 0 && map.isValidPair(part, b[index])));
}

export function alignSimple(kind, baseText, workText, { map, file = '', stringTransform, schema = false }) {
  const base = lexSimple(kind, baseText);
  const work = lexSimple(kind, workText);
  const result = {
    errors: [],
    pairs: new Map(),
    singleWordLiterals: [],
    manualLiterals: [],
    commentsChanged: 0,
    tokens: base.tokens.length,
  };
  const literals = (map.explicit.literals ?? []).filter((entry) => !entry.file || file.endsWith(entry.file));
  const tokenEdits = (map.explicit.tokenEdits ?? []).filter((entry) => file.endsWith(entry.file));
  if (base.tokens.length !== work.tokens.length) {
    result.errors.push({ kind: 'tokens', line: 0, from: String(base.tokens.length), to: String(work.tokens.length) });
    return result;
  }
  // esquema JSON: nomes de propriedade também aparecem como valor (`required`) e como último trecho de `$ref`
  const schemaKeys = schema ? new Set(base.tokens.filter((token) => token.type === 'key').map((token) => unquote(token.value))) : null;
  const seen = new Map();
  for (let i = 0; i < base.tokens.length; i++) {
    const a = base.tokens[i];
    const b = work.tokens[i];
    if (a.type !== b.type) {
      result.errors.push({ kind: 'tipo-de-token', line: a.line, from: a.value, to: b.value });
      continue;
    }
    if (a.value === b.value) continue;
    if (a.type === 'ident' || a.type === 'key') {
      const nth = (seen.get(a.value) ?? 0) + 1;
      seen.set(a.value, nth);
      const edit = tokenEdits.find((entry) => entry.from === a.value && entry.to === b.value && (!entry.occurrence || entry.occurrence === nth));
      if (edit) result.manualLiterals.push({ line: a.line, from: a.value, to: b.value, reason: edit.reason });
      else if (a.type === 'key' ? compositePair(unquote(a.value), unquote(b.value), map) : compositePair(a.value, b.value, map)) result.pairs.set(a.value, b.value);
      else result.errors.push({ kind: 'par', line: a.line, from: a.value, to: b.value });
    } else if (a.type === 'string') {
      const manual = literals.find((entry) => entry.from === a.value && entry.to === b.value);
      const from = unquote(a.value);
      const to = unquote(b.value);
      if (manual) result.manualLiterals.push({ line: a.line, from: a.value, to: b.value, reason: manual.reason });
      else if (schemaKeys && (schemaKeys.has(from) || (from.startsWith('#/') && schemaKeys.has(from.split('/').at(-1)))) && schemaRefPair(from, to, map)) result.singleWordLiterals.push({ line: a.line, from: a.value, to: b.value });
      else if (stringTransform && stringTransform(from) === to) result.singleWordLiterals.push({ line: a.line, from: '(texto com nome citado)', to: '(só o nome citado mudou)' });
      else if (kind !== 'json' && (isSingleWord(from) || isIdentifierWord(from))) {
        if (compositePair(from, to, map) && a.value[0] === b.value[0]) result.singleWordLiterals.push({ line: a.line, from: a.value, to: b.value });
        else result.errors.push({ kind: 'literal', line: a.line, from: a.value, to: b.value });
      } else if (looksLikeCode(from) && mapEmbedded(from, map) === to) {
        result.singleWordLiterals.push({ line: a.line, from: '(código embutido)', to: '(identificadores pelo mapa)' });
      } else result.errors.push({ kind: 'literal', line: a.line, from: a.value, to: b.value });
    } else {
      const edit = tokenEdits.find((entry) => entry.from === a.value && entry.to === b.value);
      if (edit) result.manualLiterals.push({ line: a.line, from: '(trecho opaco)', to: '(editado)', reason: edit.reason });
      else result.errors.push({ kind: 'token', line: a.line, from: a.value, to: b.value });
    }
  }
  const baseComments = new Set(base.comments.map((comment) => comment.text));
  result.commentsChanged = work.comments.filter((comment) => !baseComments.has(comment.text)).length;
  return result;
}

/**
 * Alinhamento restrito: só aceita diferença de identificador cujo nome ANTIGO está em `only` (conjunto fechado, como os
 * nomes de campo dos prompts ou as variáveis de ambiente de `identifier-map.md` §6). Qualquer outra diferença reprova.
 */
export function alignRestricted(kind, baseText, workText, { map, file = '', only, wordLevel = false, stringTransform }) {
  if (!wordLevel) {
    const result = alignSimple(kind, baseText, workText, { map, file, stringTransform });
    // par aceito pelo mapa mas fora do conjunto fechado vira erro
    for (const [antigo] of result.pairs) if (!only.has(antigo)) result.errors.push({ kind: 'par-fora-do-conjunto', line: 0, from: antigo, to: result.pairs.get(antigo) });
    return result;
  }
  // texto corrido: palavra a palavra, ignorando espaço e pontuação, que têm que ficar idênticos
  const result = { errors: [], pairs: new Map(), singleWordLiterals: [], manualLiterals: [], commentsChanged: 0, tokens: 0 };
  const split = (text) => text.split(/([A-Za-z0-9_]+)/);
  const a = split(baseText);
  const b = split(workText);
  if (a.length !== b.length) {
    result.errors.push({ kind: 'tokens', line: 0, from: String(a.length), to: String(b.length) });
    return result;
  }
  let line = 1;
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) {
      const wordPosition = i % 2 === 1;
      if (wordPosition && only.has(a[i]) && map.isValidPair(a[i], b[i])) result.pairs.set(a[i], b[i]);
      else result.errors.push({ kind: wordPosition ? 'par' : 'token', line, from: a[i], to: b[i] });
    }
    line += (a[i].match(/\n/g) ?? []).length;
    result.tokens += i % 2;
  }
  return result;
}
