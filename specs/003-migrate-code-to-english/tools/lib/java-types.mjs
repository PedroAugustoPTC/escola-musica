// Análise mínima dos tipos de um arquivo Java, sobre os tokens de `java-lexer.mjs` (T026, T028, T030).
//
// `scanTypes(tokens)` devolve cada `class`, `interface`, `enum` e `record` (inclusive aninhados) com:
//   path        nome qualificado pelo arquivo: `ConteudoRelatorioSemestral.Tecnica`
//   kind        class | interface | enum | record
//   nameIndex   índice, em `tokens`, do token do nome do tipo
//   components  (record) [{ name, nameIndex, types }]
//   constants   (enum) [{ name, nameIndex }]

/** Lê o trecho balanceado que começa no token `open` e devolve [índiceDoFechamento, índiceInicial, índiceFinal). */
export function balanced(tokens, openIndex, open, close) {
  let depth = 0;
  for (let i = openIndex; i < tokens.length; i++) {
    if (tokens[i].type !== 'op') continue;
    if (tokens[i].value === open) depth++;
    else if (tokens[i].value === close && --depth === 0) return [i, openIndex + 1, i];
  }
  return [tokens.length - 1, openIndex + 1, tokens.length];
}

/** Divide `tokens[from, to)` por vírgulas do nível zero (parênteses, colchetes, chaves e genéricos). */
export function splitTopLevel(tokens, from, to) {
  const parts = [];
  let current = [];
  let depth = 0;
  for (let i = from; i < to; i++) {
    const token = tokens[i];
    if (token.type === 'op' && '(<[{'.includes(token.value)) depth++;
    if (token.type === 'op' && ')>]}'.includes(token.value)) depth--;
    if (token.type === 'op' && token.value === ',' && depth === 0) {
      parts.push(current);
      current = [];
    } else current.push({ token, index: i });
  }
  if (current.length) parts.push(current);
  return parts;
}

export function scanTypes(tokens) {
  const types = [];
  const stack = [];
  let depth = 0;
  let pending = null;
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];
    const previous = tokens[i - 1];
    if (
      token.type === 'ident' &&
      ['class', 'interface', 'enum', 'record'].includes(token.value) &&
      tokens[i + 1]?.type === 'ident' &&
      !(previous?.type === 'op' && previous.value === '.')
    ) {
      pending = { name: tokens[i + 1].value, kind: token.value, nameIndex: i + 1 };
    } else if (token.type === 'op' && token.value === '{') {
      depth++;
      if (!pending) continue;
      stack.push({ name: pending.name, depth });
      const entry = { path: stack.map((frame) => frame.name).join('.'), kind: pending.kind, nameIndex: pending.nameIndex };
      if (pending.kind === 'record') {
        const open = tokens.findIndex((candidate, index) => index > pending.nameIndex && candidate.type === 'op' && candidate.value === '(');
        const [, from, to] = balanced(tokens, open, '(', ')');
        entry.components = splitTopLevel(tokens, from, to).map((part) => {
          const words = part.filter(({ token: t }) => t.type === 'ident');
          const last = words.at(-1);
          return { name: last?.token.value, nameIndex: last?.index, types: words.slice(0, -1).map(({ token: t }) => t.value) };
        });
      }
      if (pending.kind === 'enum') {
        const [, from, to] = balanced(tokens, i, '{', '}');
        entry.constants = [];
        let level = 0;
        let expectName = true;
        for (let j = from; j < to; j++) {
          const inner = tokens[j];
          if (inner.type === 'op' && '({'.includes(inner.value)) level++;
          if (inner.type === 'op' && '})'.includes(inner.value)) level--;
          if (level === 0 && inner.type === 'op' && inner.value === ';') break;
          if (level === 0 && inner.type === 'op' && inner.value === ',') expectName = true;
          else if (level === 0 && expectName && inner.type === 'ident') {
            entry.constants.push({ name: inner.value, nameIndex: j });
            expectName = false;
          }
        }
      }
      types.push(entry);
      pending = null;
    } else if (token.type === 'op' && token.value === '}') {
      if (stack.length && stack.at(-1).depth === depth) stack.pop();
      depth--;
    }
  }
  return types;
}
