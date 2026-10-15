#!/usr/bin/env node
// Inventário de palavras de identificadores (tasks T004/T008, research.md R-05).
//
//   node specs/003-migrate-code-to-english/tools/word-inventory.mjs [raiz] [--glossary caminho]
//
// Percorre o código do repositório, extrai as palavras de identificadores, nomes de arquivos e pastas e
// literais de código (uma palavra, sem espaço nem acento), ignorando comentários e texto para pessoas, e imprime
// a contagem por palavra com um arquivo de exemplo. As palavras que o glossário já conhece são marcadas
// `[glossário]`; as demais precisam ser classificadas à mão (português ou inglês) antes de ligar o guardião.
// Nunca imprime valores de texto, só palavras de identificadores.
import { createRequire } from 'node:module';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, extname, join, relative, sep } from 'node:path';
import { isTextForPeople, loadGlossary, lookup, splitWords } from './lib/words.mjs';

const args = process.argv.slice(2);
const glossaryFlag = args.indexOf('--glossary');
const glossaryPath = glossaryFlag >= 0 ? args.splice(glossaryFlag, 2)[1] : null;
const root = args[0] ?? process.cwd();

const SKIP_DIRS = new Set([
  '.git', 'node_modules', 'target', 'dist', 'build', 'playwright-report', 'test-results', '.claude',
  'portal-do-professor-design-system', 'assets', '.specify', '.idea', '.vscode',
]);
const SKIP_FILES = new Set(['package-lock.json', 'tsconfig.tsbuildinfo', 'api-tipos.ts', 'api-types.ts']);
const CODE_EXTENSIONS = new Set([
  '.java', '.ts', '.tsx', '.mjs', '.js', '.css', '.sql', '.yaml', '.yml', '.json', '.properties', '.sh',
  '.xml', '.html',
]);
const SPECIAL_NAMES = new Set(['Dockerfile', 'Caddyfile', '.env.example', '.gitignore', '.dockerignore']);

/** Sintaxe de comentários e aspas por tipo de arquivo. */
function syntaxOf(file) {
  const extension = extname(file);
  const name = basename(file);
  if (['.java', '.ts', '.tsx', '.mjs', '.js', '.css'].includes(extension))
    return { line: ['//'], block: [['/*', '*/']], quotes: ['"', "'", '`'] , textBlocks: extension === '.java' };
  if (extension === '.sql') return { line: ['--'], block: [['/*', '*/']], quotes: ["'", '"'] };
  if (extension === '.json') return { line: [], block: [], quotes: ['"'] };
  if (extension === '.xml' || extension === '.html')
    return { line: [], block: [['<!--', '-->']], quotes: ['"', "'"] };
  if (['.yaml', '.yml', '.properties', '.sh'].includes(extension) || SPECIAL_NAMES.has(name))
    return { line: ['#'], block: [], quotes: ['"', "'"] };
  return { line: [], block: [], quotes: [] };
}

/** Extrai os pedaços de código de um texto: identificadores soltos e literais que não são texto para pessoas. */
function extractFragments(text, syntax) {
  const fragments = [];
  let i = 0;
  let code = '';
  const flush = () => {
    if (code) fragments.push(code);
    code = '';
  };
  while (i < text.length) {
    const rest = text.startsWith.bind(text);
    const lineMarker = syntax.line.find((marker) => rest(marker, i));
    if (lineMarker && !(lineMarker === '//' && text[i - 1] === ':')) {
      flush();
      while (i < text.length && text[i] !== '\n') i++;
      continue;
    }
    const blockMarker = syntax.block.find(([open]) => rest(open, i));
    if (blockMarker) {
      flush();
      const end = text.indexOf(blockMarker[1], i + blockMarker[0].length);
      i = end < 0 ? text.length : end + blockMarker[1].length;
      continue;
    }
    if (syntax.textBlocks && rest('"""', i)) {
      flush();
      const end = text.indexOf('"""', i + 3);
      const body = text.slice(i + 3, end < 0 ? text.length : end);
      // bloco de texto: só as palavras entre aspas sem espaço (chaves/valores de JSON, nomes de SQL)
      for (const match of body.matchAll(/"([^"\s]+)"/g)) if (!isTextForPeople(match[1])) fragments.push(match[1]);
      i = end < 0 ? text.length : end + 3;
      continue;
    }
    const quote = syntax.quotes.find((q) => text[i] === q);
    if (quote && !(quote === "'" && /[A-Za-z]/.test(text[i - 1] ?? '') && /[A-Za-z]/.test(text[i + 1] ?? ''))) {
      flush();
      let j = i + 1;
      let literal = '';
      while (j < text.length && text[j] !== quote && (quote === '`' || text[j] !== '\n')) {
        if (text[j] === '\\') {
          literal += text[j + 1] ?? '';
          j += 2;
          continue;
        }
        literal += text[j++];
      }
      if (text[j] === quote) {
        if (!isTextForPeople(literal)) fragments.push(literal);
        i = j + 1;
      } else {
        i++; // aspas soltas (apóstrofo em prosa): segue como código
      }
      continue;
    }
    code += text[i++];
  }
  flush();
  return fragments;
}

/** TypeScript/JSX: usa o compilador (ignora comentários e texto de JSX); sem ele, cai no extrator genérico. */
let ts = null;
try {
  ts = createRequire(join(root, 'frontend/package.json'))('typescript');
} catch {
  ts = null;
}

function extractTypeScript(text, file) {
  if (!ts) return null;
  const kind = file.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS;
  const source = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, kind);
  const fragments = [];
  const visit = (node) => {
    if (ts.isIdentifier(node) || ts.isPrivateIdentifier(node)) fragments.push(node.text);
    else if (
      ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node) || ts.isTemplateHead(node) ||
      ts.isTemplateMiddle(node) || ts.isTemplateTail(node)
    ) {
      if (!isTextForPeople(node.text)) fragments.push(node.text);
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return fragments;
}

/** .properties: só as chaves e os marcadores {nome}; os valores são texto para pessoas. */
function extractProperties(text) {
  const fragments = [];
  for (const line of text.split(/\r?\n/)) {
    if (/^\s*[#!]/.test(line)) continue;
    const match = /^([^=:\s]+)\s*[=:]/.exec(line);
    if (match) fragments.push(match[1]);
    for (const marker of line.matchAll(/\{([A-Za-z]+)\}/g)) fragments.push(marker[1]);
  }
  return fragments;
}

/** HTML/XML: tags, atributos e expressões ${…}; o texto entre as tags só entra em XML (pom, logback). */
function extractMarkup(text, keepText) {
  const clean = text.replace(/<!--[\s\S]*?-->/g, '');
  const fragments = [];
  for (const tag of clean.matchAll(/<\/?([A-Za-z][\w:.-]*)((?:\s+[^>]*?)?)\/?>/g)) {
    fragments.push(tag[1]);
    for (const attribute of tag[2].matchAll(/([A-Za-z_:][\w:.-]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'))?/g)) {
      fragments.push(attribute[1]);
      const value = attribute[2] ?? attribute[3];
      if (value === undefined) continue;
      if (/^([a-z-]*class|id)$/.test(attribute[1])) fragments.push(...value.split(/\s+/));
      else if (!isTextForPeople(value)) fragments.push(value);
      for (const expression of value.matchAll(/[$*#@]\{([^}]*)\}/g)) fragments.push(expression[1]);
    }
  }
  if (keepText) {
    for (const node of clean.matchAll(/>([^<]+)</g)) {
      const value = node[1].trim();
      if (value && !isTextForPeople(value)) fragments.push(value);
    }
  }
  return fragments;
}

const YAML_QUOTES = { line: [], block: [], quotes: ['"', "'"] };

function extractYaml(text) {
  const fragments = [];
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.replace(/(^|\s)#.*$/, '');
    const match = /^\s*(?:-\s+)?([^\s:#"'][^:]*?):(?:\s+(.*))?$/.exec(line);
    if (!match) {
      fragments.push(...extractFragments(line.replace(/^\s*-\s+/, ''), YAML_QUOTES));
      continue;
    }
    fragments.push(match[1]);
    const value = (match[2] ?? '').trim();
    if (!value) continue;
    // valor sem aspas e com espaço ou acento é texto para pessoas; entre aspas, vale a regra do literal
    if (/^["']/.test(value)) fragments.push(...extractFragments(value, YAML_QUOTES));
    else if (!isTextForPeople(value)) fragments.push(value);
  }
  return fragments;
}

function* walk(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue;
    const full = join(directory, entry.name);
    if (entry.isDirectory()) yield* walk(full);
    else if (entry.isFile()) yield full;
  }
}

function inScope(relativePath) {
  const parts = relativePath.split('/');
  // specs/: só os contratos de fio (specs/**/contracts/*.yaml|json)
  if (parts[0] === 'specs') return parts.includes('contracts') && /\.(ya?ml|json)$/.test(relativePath);
  return true;
}

const glossary = glossaryPath ? loadGlossary(glossaryPath) : loadGlossary(join(root, '.specify/memory/glossary.json'));
const inventory = new Map();

function record(word, sample) {
  let entry = inventory.get(word);
  if (!entry) {
    entry = { count: 0, sample };
    inventory.set(word, entry);
  }
  entry.count++;
}

for (const file of walk(root)) {
  const relativePath = relative(root, file).split(sep).join('/');
  const name = basename(file);
  if (SKIP_FILES.has(name) || !inScope(relativePath)) continue;
  const isCode = CODE_EXTENSIONS.has(extname(file)) || SPECIAL_NAMES.has(name);
  // nomes de pastas e do arquivo
  for (const segment of relativePath.split('/')) for (const word of splitWords(segment)) record(word, relativePath);
  if (!isCode || statSync(file).size > 2_000_000) continue;
  const content = readFileSync(file, 'utf8');
  const extension = extname(file);
  let fragments;
  if (['.ts', '.tsx'].includes(extension)) fragments = extractTypeScript(content, file) ?? extractFragments(content, syntaxOf(file));
  else if (extension === '.properties') fragments = extractProperties(content);
  else if (extension === '.html') fragments = extractMarkup(content, false);
  else if (extension === '.xml') fragments = extractMarkup(content, true);
  else if (['.yaml', '.yml'].includes(extension)) fragments = extractYaml(content);
  else fragments = extractFragments(content, syntaxOf(file));
  for (const fragment of fragments) for (const word of splitWords(fragment)) record(word, relativePath);
}

const rows = [...inventory.entries()].sort(([a], [b]) => a.localeCompare(b));
const known = rows.filter(([word]) => lookup(glossary, word));
const unknown = rows.filter(([word]) => !lookup(glossary, word));
console.log(`# Inventário de palavras (${rows.length} distintas; ${known.length} no glossário, ${unknown.length} a classificar)`);
console.log('# formato: palavra<TAB>ocorrências<TAB>exemplo');
console.log('\n## No glossário');
for (const [word, { count, sample }] of known) console.log(`${word}\t${count}\t${sample}`);
console.log('\n## A classificar (português ou inglês?)');
for (const [word, { count, sample }] of unknown) console.log(`${word}\t${count}\t${sample}`);
