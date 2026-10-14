// Tokenizadores simples para os arquivos que não são Java (T053): SQL, YAML, `.properties`, shell, `Dockerfile`, XML/HTML,
// CSS, JSON e texto. Servem ao `rename-check` e ao gerador de mapa: separam
//   ident    palavra de identificador (nome de tabela, coluna, chave de configuração, variável de ambiente, classe CSS…)
//   string   literal de texto entre aspas (idêntico byte a byte; só vira par do mapa se for UMA palavra de identificador)
//   text     texto corrido fora de aspas (XML/HTML e `text`): palavras viram `ident`, o resto fica em `op`
//   number, op
// e deixam os comentários à parte (a documentação segue em português).
//
//   const { tokens, comments } = lexSimple('yaml', texto);
//
// Cada token traz `type`, `value` e `line`. Um identificador pode conter `-` e `.` no meio (`fuso-horario`,
// `relatorios.escola.fuso-horario`, `ia-real`) para as linguagens de configuração; em SQL o `.` é operador.

/** Sintaxe por tipo de arquivo. */
const SYNTAX = {
  sql: { line: ['--'], block: [['/*', '*/']], quotes: ["'"], identQuote: '"', dollar: true, joiners: '' },
  yaml: { line: ['#'], block: [], quotes: ["'", '"'], joiners: '.-' },
  properties: { line: ['#', '!'], block: [], quotes: [], joiners: '.-', properties: true },
  shell: { line: ['#'], block: [], quotes: ["'", '"'], joiners: '-' },
  dockerfile: { line: ['#'], block: [], quotes: ["'", '"'], joiners: '-.' },
  xml: { line: [], block: [['<!--', '-->']], quotes: ["'", '"'], joiners: '-.:', markup: true },
  html: { line: [], block: [['<!--', '-->']], quotes: ["'", '"'], joiners: '-', markup: true },
  css: { line: [], block: [['/*', '*/']], quotes: ["'", '"'], joiners: '-' },
  json: { line: [], block: [], quotes: ['"'], joiners: '' },
  text: { line: [], block: [], quotes: [], joiners: '' },
};

const IDENT_START = /[A-Za-z_]/;
const IDENT_PART = /[A-Za-z0-9_]/;
const DIGIT = /[0-9]/;

export function kindOf(path) {
  const name = path.split(/[\\/]/).at(-1);
  if (/\.sql$/.test(name)) return 'sql';
  if (/\.ya?ml$/.test(name)) return 'yaml';
  if (/\.properties$/.test(name) || name === '.env.example' || /^\.env/.test(name)) return 'properties';
  if (/\.(sh|bash)$/.test(name)) return 'shell';
  if (name === 'Dockerfile' || /^Dockerfile\./.test(name)) return 'dockerfile';
  if (/\.(xml|pom)$/.test(name) || name === 'pom.xml') return 'xml';
  if (/\.html?$/.test(name)) return 'html';
  if (/\.css$/.test(name)) return 'css';
  if (/\.json$/.test(name)) return 'json';
  return 'text';
}

/** Lê um literal entre aspas a partir de `start` (na aspa de abertura); devolve o índice logo depois do fechamento. */
function readQuoted(source, start, quote, doubledEscape) {
  let i = start + 1;
  while (i < source.length) {
    const char = source[i];
    if (char === '\\' && !doubledEscape) i += 2;
    else if (char === quote) {
      if (doubledEscape && source[i + 1] === quote) i += 2;
      else return i + 1;
    } else i++;
  }
  return source.length;
}

export function lexSimple(kind, source) {
  const syntax = SYNTAX[kind];
  if (!syntax) throw new Error(`Tipo de arquivo desconhecido: ${kind}`);
  const tokens = [];
  const comments = [];
  let line = 1;
  let i = 0;
  const advance = (to) => {
    for (; i < to; i++) if (source[i] === '\n') line++;
  };
  let atLineStart = true;
  let inTag = false;
  while (i < source.length) {
    const char = source[i];
    if (char === '\n') {
      line++;
      i++;
      atLineStart = true;
      continue;
    }
    if (char === ' ' || char === '\t' || char === '\r') {
      i++;
      continue;
    }
    const wasLineStart = atLineStart;
    atLineStart = false;
    const start = i;
    // comentários
    const lineMarker = syntax.line.find((marker) => source.startsWith(marker, i) && (marker !== '#' || kind !== 'shell' || wasLineStart || /\s/.test(source[i - 1] ?? ' ')) && (kind !== 'yaml' || wasLineStart || /\s/.test(source[i - 1] ?? ' ')));
    if (lineMarker) {
      const end = source.indexOf('\n', i);
      const stop = end < 0 ? source.length : end;
      comments.push({ text: source.slice(i, stop).replace(/\r$/, ''), line });
      i = stop;
      continue;
    }
    const blockMarker = syntax.block.find(([open]) => source.startsWith(open, i));
    if (blockMarker) {
      const end = source.indexOf(blockMarker[1], i + blockMarker[0].length);
      const stop = end < 0 ? source.length : end + blockMarker[1].length;
      comments.push({ text: source.slice(i, stop), line });
      advance(stop);
      continue;
    }
    // XML/HTML: fora de uma marcação o texto corrido é um token só (comparado byte a byte)
    if (syntax.markup && !inTag && char !== '<') {
      let stop = source.indexOf('<', i);
      if (stop < 0) stop = source.length;
      const raw = source.slice(i, stop);
      if (raw.trim()) tokens.push({ type: 'text', value: raw.trim(), line, offset: start + (raw.length - raw.trimStart().length) });
      advance(stop);
      continue;
    }
    if (syntax.markup && char === '<') {
      inTag = true;
      tokens.push({ type: 'op', value: char, line, offset: start });
      i++;
      continue;
    }
    if (syntax.markup && inTag && char === '>') {
      inTag = false;
      tokens.push({ type: 'op', value: char, line, offset: start });
      i++;
      continue;
    }
    // SQL: texto entre `$` (corpo de função) é literal
    if (syntax.dollar && source.startsWith('$$', i)) {
      const end = source.indexOf('$$', i + 2);
      const stop = end < 0 ? source.length : end + 2;
      tokens.push({ type: 'string', value: source.slice(i, stop), line, offset: start });
      advance(stop);
      continue;
    }
    // literal entre aspas
    if (syntax.quotes.includes(char)) {
      const stop = readQuoted(source, i, char, kind === 'sql');
      tokens.push({ type: 'string', value: source.slice(i, stop), line, offset: start });
      advance(stop);
      continue;
    }
    // SQL: identificador entre aspas duplas é identificador
    if (syntax.identQuote && char === syntax.identQuote) {
      const stop = readQuoted(source, i, char, true);
      tokens.push({ type: 'ident', value: source.slice(i + 1, stop - 1), line, quoted: true, offset: start + 1 });
      advance(stop);
      continue;
    }
    if (IDENT_START.test(char)) {
      let j = i + 1;
      while (j < source.length) {
        if (IDENT_PART.test(source[j])) j++;
        else if (syntax.joiners.includes(source[j]) && IDENT_PART.test(source[j + 1] ?? '') && !DIGIT.test('')) j++;
        else break;
      }
      tokens.push({ type: 'ident', value: source.slice(i, j), line, offset: start });
      i = j;
      continue;
    }
    if (DIGIT.test(char)) {
      let j = i + 1;
      while (j < source.length && /[0-9.eE_]/.test(source[j])) j++;
      tokens.push({ type: 'number', value: source.slice(i, j), line, offset: start });
      i = j;
      continue;
    }
    tokens.push({ type: 'op', value: char, line, offset: start });
    i++;
  }
  if (kind === 'json') {
    // chave de objeto: literal seguido de ":" (as chaves são identificadores; os valores são texto)
    tokens.forEach((token, index) => {
      if (token.type === 'string' && tokens[index + 1]?.type === 'op' && tokens[index + 1].value === ':') token.type = 'key';
    });
  }
  return { tokens, comments };
}

/** Uma palavra de identificador (inclusive `snake_case`, `kebab-case` e chave pontilhada). */
export function isIdentifierWord(text) {
  return /^[A-Za-z_][A-Za-z0-9_]*(?:[.-][A-Za-z0-9_]+)*$/.test(text);
}

/** Conteúdo de um literal entre aspas, sem as aspas (SQL duplica a aspa simples). */
export function unquote(literal) {
  if (literal.length >= 2 && (literal[0] === "'" || literal[0] === '"') && literal.at(-1) === literal[0]) return literal.slice(1, -1);
  return literal;
}
