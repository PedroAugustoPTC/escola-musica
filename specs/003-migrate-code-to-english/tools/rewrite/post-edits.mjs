#!/usr/bin/env node
// Aplica as edições que a receita de renomeação não faz e que o compilador não acusa (T039, T041).
//
//   node post-edits.mjs [--root <raiz>] [--check]
//
// Fonte: `tools/explicit-pairs.json`, chaves
//   literals    { file, from, to, reason }  literal de texto Java igual a `from` → `to` (nomes de pacote e de sufixo em
//                                            regras ArchUnit e em decoradores que casam classe por nome);
//   tokenEdits  { file, from, to, reason }  identificador Java `from` → `to` (ex.: startsWith → endsWith);
//   textEdits   { file, from, to, reason }  texto exato em arquivo que não é Java (logback, application*.yaml).
// `file` é um sufixo de caminho único dentro de `backend/`; `occurrence` (opcional, 1-based) escolhe uma só ocorrência. Toda entrada TEM de casar ao menos uma vez (senão é entrada
// morta e o script falha); `textEdits` casa exatamente uma vez. Com `--check` só confere, sem gravar.
// O `rename-check` aceita essas mesmas entradas (e só elas) como exceção ao "literal alterado" / "par fora do mapa".
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import { lex } from '../lib/java-lexer.mjs';

const args = process.argv.slice(2);
const root = resolve(args.includes('--root') ? args[args.indexOf('--root') + 1] : process.cwd());
const check = args.includes('--check');
const explicit = JSON.parse(readFileSync(join(root, 'specs/003-migrate-code-to-english/tools/explicit-pairs.json'), 'utf8'));

const files = [];
(function walk(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.name === 'target' || entry.name === 'node_modules') continue;
    const full = join(directory, entry.name);
    if (entry.isDirectory()) walk(full);
    else files.push(full);
  }
})(join(root, 'backend'));
const rel = (file) => relative(root, file).split(sep).join('/');

const findFile = (suffix) => {
  const matches = files.filter((file) => rel(file).endsWith(suffix));
  if (matches.length !== 1) throw new Error(`"${suffix}" casa ${matches.length} arquivos (esperado 1)`);
  return matches[0];
};

const pending = new Map(); // arquivo → { source, edits: [{ start, end, text }] }
const edit = (file, start, end, text) => {
  if (!pending.has(file)) pending.set(file, { source: readFileSync(file, 'utf8'), edits: [] });
  pending.get(file).edits.push({ start, end, text });
};

const problems = [];
let applied = 0;
for (const [kind, tokenType] of [['literals', 'string'], ['tokenEdits', 'ident']]) {
  for (const entry of explicit[kind] ?? []) {
    const file = findFile(entry.file);
    if (!entry.reason) problems.push(`${kind}: ${entry.file} "${entry.from}" sem motivo`);
    const source = readFileSync(file, 'utf8');
    const hits = lex(source).tokens.filter((token) => token.type === tokenType && token.value === entry.from);
    if (hits.length === 0) {
      problems.push(`${kind}: ${entry.file} "${entry.from}" não casa nada (entrada morta)`);
      continue;
    }
    // `occurrence` (1-based) restringe a troca a uma ocorrência de `from` no arquivo; sem ele vale para todas
    const chosen = entry.occurrence ? hits.filter((_, index) => index + 1 === entry.occurrence) : hits;
    if (chosen.length === 0) problems.push(`${kind}: ${entry.file} "${entry.from}" não tem a ocorrência ${entry.occurrence}`);
    for (const token of chosen) {
      const quote = tokenType === 'string' ? 1 : 0;
      edit(file, token.offset + quote, token.offset + quote + token.value.length, entry.to);
      applied++;
    }
  }
}
for (const entry of explicit.textEdits ?? []) {
  const file = findFile(entry.file);
  if (!entry.reason) problems.push(`textEdits: ${entry.file} sem motivo`);
  const source = readFileSync(file, 'utf8');
  const first = source.indexOf(entry.from);
  if (first < 0 || source.indexOf(entry.from, first + 1) >= 0) {
    problems.push(`textEdits: ${entry.file} "${entry.from}" deve casar exatamente uma vez`);
    continue;
  }
  edit(file, first, first + entry.from.length, entry.to);
  applied++;
}

if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
if (!check) {
  for (const [file, { source, edits }] of pending) {
    let text = source;
    for (const { start, end, text: replacement } of edits.sort((a, b) => b.start - a.start)) text = text.slice(0, start) + replacement + text.slice(end);
    writeFileSync(file, text);
  }
}
console.log(`${applied} edições ${check ? 'conferidas' : 'aplicadas'} em ${pending.size} arquivos`);
