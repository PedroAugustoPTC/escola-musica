// Léxico Java mínimo para as ferramentas da feature 003 (T027). Sem dependências.
//
// Tokeniza o fonte em identificadores, literais (texto, bloco de texto, caractere, número), operadores e anotações,
// descarta comentários e espaços (os comentários saem em uma lista à parte, só para conferência) e agrupa os blocos de
// `import`, que o Spotless reordena depois da renomeação.
//
//   const { tokens, comments, imports } = lex(fonte);
//
// Tipos de token: 'ident' (inclui palavras-chave, que nunca são renomeadas), 'string', 'textblock', 'char', 'number',
// 'op' e 'annotation' (o `@` junto do nome: `@Override`).

const OPERATORS = [
  '>>>=', '<<=', '>>=', '>>>', '...', '->', '::', '++', '--', '&&', '||', '==', '!=', '<=', '>=', '+=', '-=', '*=',
  '/=', '&=', '|=', '^=', '%=', '<<', '>>',
];

const isIdentStart = (c) => /[\p{L}_$]/u.test(c);
const isIdentPart = (c) => /[\p{L}\p{N}_$]/u.test(c);

/** Lê um literal numérico (decimal, hexadecimal, binário, com `_`, expoente e sufixo). */
function readNumber(source, start) {
  let i = start;
  if (source[i] === '0' && /[xXbB]/.test(source[i + 1] ?? '')) {
    i += 2;
    while (i < source.length && /[0-9a-fA-F_]/.test(source[i])) i++;
  } else {
    while (i < source.length && /[0-9_]/.test(source[i])) i++;
    if (source[i] === '.' && /[0-9]/.test(source[i + 1] ?? '')) {
      i++;
      while (i < source.length && /[0-9_]/.test(source[i])) i++;
    }
    if (/[eE]/.test(source[i] ?? '') && /[0-9+-]/.test(source[i + 1] ?? '')) {
      i += 2;
      while (i < source.length && /[0-9_]/.test(source[i])) i++;
    }
  }
  if (/[lLfFdD]/.test(source[i] ?? '')) i++;
  return i;
}

export function lex(source) {
  const tokens = [];
  const comments = [];
  let i = 0;
  let line = 1;
  const push = (type, value, start) => tokens.push({ type, value, line, offset: start });
  const advanceLines = (text) => {
    for (const c of text) if (c === '\n') line++;
  };

  while (i < source.length) {
    const c = source[i];
    if (c === '\n') {
      line++;
      i++;
    } else if (/\s/.test(c)) {
      i++;
    } else if (source.startsWith('//', i)) {
      let end = source.indexOf('\n', i);
      if (end < 0) end = source.length;
      comments.push({ line, text: source.slice(i, end) });
      i = end;
    } else if (source.startsWith('/*', i)) {
      let end = source.indexOf('*/', i + 2);
      end = end < 0 ? source.length : end + 2;
      const text = source.slice(i, end);
      comments.push({ line, text });
      advanceLines(text);
      i = end;
    } else if (source.startsWith('"""', i)) {
      let end = i + 3;
      while (end < source.length && !(source.startsWith('"""', end) && source[end - 1] !== '\\')) end++;
      const text = source.slice(i + 3, end);
      push('textblock', text, i);
      advanceLines(text);
      i = end + 3;
    } else if (c === '"') {
      let end = i + 1;
      while (end < source.length && source[end] !== '"') end += source[end] === '\\' ? 2 : 1;
      push('string', source.slice(i + 1, end), i);
      i = end + 1;
    } else if (c === "'") {
      let end = i + 1;
      while (end < source.length && source[end] !== "'") end += source[end] === '\\' ? 2 : 1;
      push('char', source.slice(i + 1, end), i);
      i = end + 1;
    } else if (/[0-9]/.test(c) || (c === '.' && /[0-9]/.test(source[i + 1] ?? ''))) {
      const end = readNumber(source, i);
      push('number', source.slice(i, end), i);
      i = end;
    } else if (isIdentStart(c)) {
      let end = i + 1;
      while (end < source.length && isIdentPart(source[end])) end++;
      push('ident', source.slice(i, end), i);
      i = end;
    } else if (c === '@' && isIdentStart(source[i + 1] ?? '')) {
      let end = i + 2;
      while (end < source.length && (isIdentPart(source[end]) || source[end] === '.')) end++;
      push('annotation', source.slice(i, end), i);
      i = end;
    } else {
      const operator = OPERATORS.find((candidate) => source.startsWith(candidate, i)) ?? c;
      push('op', operator, i);
      i += operator.length;
    }
  }
  return { tokens, comments, ...splitImports(tokens) };
}

/**
 * Separa os `import` iniciais (e o `package`) do resto. `imports` é a lista de declarações, cada uma como lista de
 * tokens sem o `;`; `body` são os tokens restantes (incluindo `package`), prontos para alinhar token a token.
 */
function splitImports(tokens) {
  const imports = [];
  const body = [];
  let index = 0;
  while (index < tokens.length) {
    const token = tokens[index];
    if (token.type === 'ident' && token.value === 'import') {
      const declaration = [];
      index++;
      while (index < tokens.length && !(tokens[index].type === 'op' && tokens[index].value === ';')) {
        declaration.push(tokens[index]);
        index++;
      }
      index++;
      imports.push(declaration);
    } else {
      body.push(token);
      index++;
    }
  }
  return { imports, body };
}

/** Texto canônico de uma declaração de import (`static a.b.C.d`), para comparar e ordenar. */
export const importText = (declaration) => declaration.map((token) => token.value).join('');

/** Palavras de um literal de texto ou bloco: usado pelo verificador para separar texto de código embutido. */
export const isSingleWord = (text) => /^[A-Za-z_$][\w$.-]*$/.test(text);
