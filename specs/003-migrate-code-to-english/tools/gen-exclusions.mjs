#!/usr/bin/env node
// Gera e encolhe `.specify/memory/glossary-temporary-exclusions.json` (FR-035, tasks T005/T018/T044…).
//
// Lê os relatórios dos três guardiões (modo `GUARD_REPORT=1`) e grava a lista de exclusões temporárias:
//   - `files`: arquivos inteiros ainda em português (existentes na `main`), com a fatia dona;
//   - `terms`: termos retidos (B1/B2/B3) dentro de arquivos já migrados, com dono = fatia 4.
//
//   node gen-exclusions.mjs --init --existing /tmp/main-files.txt        # primeira vez (fatia 1)
//   node gen-exclusions.mjs [--held-back tools/held-back/slice-2.json]   # a cada fatia: só encolhe
//
// Regras: arquivo sem violação sai da lista; arquivo cujas violações restantes são todas termos retidos vira
// entradas de termo; arquivo novo com violação NÃO entra (é erro: o guardião tem de pegá-lo); nunca acrescenta
// termo novo a arquivo já migrado sem `--allow-new`. Código de saída 1 quando há violação fora da lista.
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const option = (name, fallback = null) => {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : fallback;
};

const root = resolve(option('--root', process.cwd()));
const outPath = resolve(root, option('--out', '.specify/memory/glossary-temporary-exclusions.json'));
const reportPaths = (option('--reports') ?? [
  'backend/aplicacao/target/guard-violations-java.json',
  'backend/aplicacao/target/guard-violations-artifacts.json',
  'frontend/test-results/guard-violations.json',
].join(',')).split(',').map((path) => resolve(root, path));
const init = flag('--init');
const allowNew = flag('--allow-new');
const heldBackPath = option('--held-back');
const existingList = option('--existing');
// Fatia que MOVE arquivos: `--remap de=>para,…` reescreve, em ordem, o início dos caminhos da lista anterior;
// `--migrated-prefix p1,p2` marca a área já migrada: ali só podem sobrar literais (a receita nunca os altera) e
// identificadores da lista de retenção (B1/B2); qualquer outro identificador é erro.
const remaps = (option('--remap') ?? '').split(',').filter(Boolean).map((pair) => pair.split('=>'));
const migratedPrefixes = (option('--migrated-prefix') ?? '').split(',').filter(Boolean);

const owners = JSON.parse(readFileSync(join(here, 'slice-owners.json'), 'utf8'));
function ownerOf(path) {
  const rule = owners.rules
    .filter(({ prefix }) => path.startsWith(prefix))
    .sort((a, b) => b.prefix.length - a.prefix.length)[0];
  return rule ? rule.slice : owners.defaultSlice;
}

// violações atuais, agrupadas por caminho
const violations = new Map();
const details = new Map(); // caminho → [{ term, kind, text }]
for (const reportPath of reportPaths) {
  if (!existsSync(reportPath)) {
    console.error(`relatório ausente: ${reportPath} (rode o guardião com GUARD_REPORT=1)`);
    process.exit(2);
  }
  for (const violation of JSON.parse(readFileSync(reportPath, 'utf8'))) {
    if (!violations.has(violation.path)) violations.set(violation.path, new Set());
    violations.get(violation.path).add(violation.term);
    if (!details.has(violation.path)) details.set(violation.path, []);
    details.get(violation.path).push({ term: violation.term, kind: violation.kind, text: violation.text });
  }
}

const heldBack = new Map();
const heldTexts = new Set(); // nomes retidos: constantes (B1), componentes de record (B2) e chaves literais (B3)
if (heldBackPath) {
  const held = JSON.parse(readFileSync(resolve(root, heldBackPath), 'utf8'));
  for (const item of held.items ?? []) heldBack.set(item.path, new Set(item.terms));
  for (const group of [held.B1, held.B2]) for (const entry of Object.values(group ?? {})) for (const member of entry.members) heldTexts.add(member);
  for (const literal of Object.keys(held.B3 ?? {})) heldTexts.add(literal);
}

const previous = !init && existsSync(outPath)
  ? JSON.parse(readFileSync(outPath, 'utf8'))
  : { version: 1, files: [], terms: [] };
const remap = (path) => remaps.reduce((current, [from, to]) => (current.startsWith(from) ? to + current.slice(from.length) : current), path);
previous.files = previous.files.map((entry) => ({ ...entry, path: remap(entry.path) }));
previous.terms = previous.terms.map((entry) => ({ ...entry, path: remap(entry.path) }));
const existingFiles = existingList ? new Set(readFileSync(resolve(root, existingList), 'utf8').split(/\r?\n/).filter(Boolean)) : null;

const next = { files: [], terms: [] };
const rejected = [];

if (init) {
  for (const [path, terms] of violations) {
    const isCatalog = path.startsWith('catalog:');
    if (!isCatalog && existingFiles && !existingFiles.has(path)) {
      rejected.push(`${path} (arquivo novo com violação: ${[...terms].join(', ')})`);
      continue;
    }
    next.files.push({ path, slice: ownerOf(path) });
  }
} else {
  const listedFiles = new Set(previous.files.map(({ path }) => path));
  for (const { path, slice } of previous.files) {
    const current = violations.get(path);
    if (!current) continue; // migrou: sai da lista
    const held = heldBack.get(path);
    if (held && [...current].every((term) => held.has(term))) {
      for (const term of [...current].sort()) next.terms.push({ path, term, slice: owners.heldBackTermSlice });
    } else {
      next.files.push({ path, slice: ownerOf(path) }); // o dono vem sempre das regras atuais
    }
  }
  for (const entry of previous.terms) {
    if (violations.get(entry.path)?.has(entry.term)) next.terms.push(entry);
  }
  // área migrada: literais e identificadores retidos viram termos (dono = fatia dos termos retidos)
  const migratedHandled = new Set();
  for (const [path, entries] of details) {
    if (!migratedPrefixes.some((prefix) => path.startsWith(prefix))) continue;
    migratedHandled.add(path);
    const seenTerms = new Set();
    for (const { term, kind, text } of entries) {
      const retained = kind === 'literal' || heldTexts.has(text);
      if (!retained) {
        rejected.push(`${path}: ${kind} "${text}" (${term}) não está na lista de retenção`);
        continue;
      }
      if (seenTerms.has(term)) continue;
      seenTerms.add(term);
      next.terms.push({ path, term, slice: owners.heldBackTermSlice });
    }
  }
  const listedTerms = new Set(next.terms.map(({ path, term }) => `${path}\u0000${term}`));
  for (const [path, terms] of violations) {
    if (listedFiles.has(path) || migratedHandled.has(path)) continue;
    for (const term of terms) {
      if (listedTerms.has(`${path}\u0000${term}`)) continue;
      if (allowNew) next.terms.push({ path, term, slice: ownerOf(path) });
      else rejected.push(`${path}: "${term}"`);
    }
  }
}

next.files.sort((a, b) => a.path.localeCompare(b.path));
next.terms.sort((a, b) => a.path.localeCompare(b.path) || a.term.localeCompare(b.term));
const output = {
  version: 1,
  description:
    'Exclusões temporárias do teste guardião de idioma (feature 003, FR-035). Só existe na branch da feature, é regerada por specs/003-migrate-code-to-english/tools/gen-exclusions.mjs, só encolhe a cada fatia e DEVE ser apagada na fatia 7. "files": arquivos ainda em português; "terms": termos retidos (B1/B2/B3) dentro de arquivos já migrados. "slice" é a fatia que quita a entrada.',
  files: next.files,
  terms: next.terms,
};
writeFileSync(outPath, JSON.stringify(output, null, 2) + '\n');

const perSlice = new Map();
for (const { slice } of [...next.files, ...next.terms]) perSlice.set(slice, (perSlice.get(slice) ?? 0) + 1);
console.log(`exclusões: ${next.files.length} arquivos, ${next.terms.length} termos retidos`);
console.log(`por fatia: ${[...perSlice].sort(([a], [b]) => a - b).map(([slice, count]) => `${slice}=${count}`).join(' ')}`);
if (rejected.length) {
  console.error(`\n${rejected.length} violação(ões) FORA da lista (arquivo novo ou termo novo):`);
  for (const line of rejected) console.error(`  ${line}`);
  process.exit(1);
}
