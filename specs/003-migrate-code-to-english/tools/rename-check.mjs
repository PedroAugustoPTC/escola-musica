#!/usr/bin/env node
// Verificador lexical de que só o NOME mudou numa fatia (research.md R-02, tasks T030).
//
//   node rename-check.mjs <árvore-base> <árvore-de-trabalho> --slice <N> [--verbose]
//
// A árvore-base é a `main` (`git archive origin/main`); a de trabalho é o repositório. Os arquivos são pareados pelo
// mapa de caminhos, tokenizados e alinhados token a token (lib/align.mjs). Saída:
//   N pares fora do mapa · N literais de texto alterados · N arquivos sem par · N colisões
// Sai com código ≠ 0 se qualquer um dos quatro (ou os de apoio: tokens, retido alterado, import) for > 0.
import { existsSync, readFileSync, readdirSync } from 'node:fs';

import { join, relative, resolve, sep } from 'node:path';
import { alignJava, collisionsOf } from './lib/align.mjs';
import { checkFiles } from './rename-check-files.mjs';
import { lex } from './lib/java-lexer.mjs';
import { loadRenameMap } from './lib/rename-map.mjs';

// modo do catálogo de mensagens: arquivo próprio, a entrada só despacha (T054)
if (process.argv.includes('--catalog')) {
  await import('./rename-check-catalog.mjs');
  process.exit(process.exitCode ?? 0);
}

/** Raízes de varredura por fatia: [raiz na base, raiz no trabalho]; a conferência é só de Java (código). */
const SLICES = {
  2: { javaRoots: [['backend/nucleo', 'backend/core'], ['backend/aplicacao', 'backend/aplicacao']] },
  // fatia 3: a aplicação muda de módulo e de pacotes; o core muda só nos componentes B2 e nas chaves persistidas; os
  // arquivos que não são Java (YAML, SQL, JSON, prompts, PDF, compose, shell…) vão por modo em rename-check-files.mjs
  3: { javaRoots: [['backend/aplicacao', 'backend/application'], ['backend/core', 'backend/core']], files: true },
};

const args = process.argv.slice(2);
const positional = args.filter((arg, index) => !arg.startsWith('--') && args[index - 1] !== '--slice');
const [baseRoot, workRoot] = positional.map((path) => resolve(path));
const slice = Number(args.includes('--slice') ? args[args.indexOf('--slice') + 1] : '0');
const verbose = args.includes('--verbose');
if (!baseRoot || !workRoot || !SLICES[slice]) {
  console.error('Uso: rename-check.mjs <árvore-base> <árvore-de-trabalho> --slice <2>');
  process.exit(2);
}

const map = loadRenameMap(workRoot, slice);
const SKIP = new Set(['target', 'node_modules', '.git']);

function* javaFiles(directory) {
  if (!existsSync(directory)) return;
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (SKIP.has(entry.name)) continue;
    const full = join(directory, entry.name);
    if (entry.isDirectory()) yield* javaFiles(full);
    else if (entry.name.endsWith('.java')) yield full;
  }
}

/** Pacote antigo de um arquivo (declaração `package`), como lista de segmentos. */
function packageSegmentsOf(source) {
  const { tokens } = lex(source);
  const start = tokens.findIndex((token) => token.type === 'ident' && token.value === 'package');
  if (start < 0) return [];
  const segments = [];
  for (let i = start + 1; i < tokens.length && !(tokens[i].type === 'op' && tokens[i].value === ';'); i++) if (tokens[i].type === 'ident') segments.push(tokens[i].value);
  return segments;
}

/** Caminho do arquivo no trabalho: raiz trocada, pasta pelo pacote novo (tabela por pacote inteiro), nome pelo mapa de tipos. */
function pairedPath(baseRelativeToRoot, baseSource, newRoot) {
  const segments = baseRelativeToRoot.split('/');
  const sourceSet = segments.slice(0, 3).join('/'); // src/main/java ou src/test/java
  const name = segments.at(-1).replace(/\.java$/, '');
  const directory = map.expectedPackage(packageSegmentsOf(baseSource)).join('/');
  return [newRoot, sourceSet, directory, `${map.mapIdent(name)}.java`].join('/');
}

const errors = { pair: [], literal: [], unpaired: [], collision: [], support: [] };
const info = { codeLiterals: new Map(), acceptedCollisions: 0, files: 0, tokens: 0, comments: 0, singleWord: 0, manual: [], pairs: new Map() };
const matchedWork = new Set();

for (const [baseRelativeRoot, workRelativeRoot] of SLICES[slice].javaRoots) {
  for (const baseFile of javaFiles(join(baseRoot, baseRelativeRoot, 'src'))) {
    const baseRelative = relative(join(baseRoot, baseRelativeRoot), baseFile).split(sep).join('/');
    const baseSource = readFileSync(baseFile, 'utf8');
    const workRelative = pairedPath(baseRelative, baseSource, workRelativeRoot);
    const workFile = join(workRoot, workRelative);
    if (!existsSync(workFile)) {
      errors.unpaired.push(`${baseRelativeRoot}/${baseRelative} → ${workRelative}: arquivo da base sem par no trabalho`);
      continue;
    }
    matchedWork.add(workRelative);
    const result = alignJava(baseSource, readFileSync(workFile, 'utf8'), { map, file: workRelative });
    info.files++;
    info.tokens += result.tokens;
    info.comments += result.commentsChanged;
    info.singleWord += result.singleWordLiterals.length;
    for (const literal of result.codeLiterals) info.codeLiterals.set(literal.kind, (info.codeLiterals.get(literal.kind) ?? 0) + 1);
    for (const manual of result.manualLiterals) info.manual.push({ file: workRelative, ...manual });
    for (const [antigo, novo] of result.pairs) info.pairs.set(antigo, novo);
    for (const error of result.errors) {
      const where = `${workRelative}:${error.line}`;
      if (error.kind === 'par') errors.pair.push(`${where}: "${error.from}" → "${error.to}" não está no mapa`);
      else if (error.kind === 'literal') errors.literal.push(`${where}: literal alterado ${JSON.stringify(error.from)} → ${JSON.stringify(error.to)}`);
      else if ((map.explicit.structuralEdits ?? []).some((entry) => workRelative.endsWith(entry.file)) && ['tokens', 'import'].includes(error.kind)) info.structural = (info.structural ?? 0) + 1;
      else errors.support.push(`${where}: ${error.kind} ${JSON.stringify(error.from)} → ${JSON.stringify(error.to)}`);
    }
    for (const collision of collisionsOf(result, workRelative)) {
      const accepted = (map.explicit.acceptedCollisions ?? []).some((entry) => workRelative.endsWith(entry.file) && entry.novo === collision.novo && JSON.stringify([...entry.antigos].sort()) === JSON.stringify(collision.antigos));
      if (accepted) {
        info.acceptedCollisions++;
        continue;
      }
      errors.collision.push(`${collision.file}: "${collision.novo}" (${collision.role}) declarado por ${collision.antigos.map((name) => `"${name}"`).join(', ')}`);
    }
  }
  for (const workFile of javaFiles(join(workRoot, workRelativeRoot, 'src'))) {
    const workRelative = relative(workRoot, workFile).split(sep).join('/');
    if (!matchedWork.has(workRelative) && !(map.explicit.newFiles ?? []).some((entry) => workRelative.endsWith(entry.file))) errors.unpaired.push(`${workRelative}: arquivo do trabalho sem origem na base`);
  }
}

if (SLICES[slice].files) {
  const heldFields = new Set();
  const heldSlice2 = JSON.parse(readFileSync(join(workRoot, 'specs/003-migrate-code-to-english/tools/held-back/slice-2.json'), 'utf8'));
  for (const entry of Object.values(heldSlice2.B2)) for (const member of entry.members) if (map.ident.has(member)) heldFields.add(member);
  const files = checkFiles({ baseRoot, workRoot, map, heldFields });
  info.nonJava = files.infos;
  for (const key of ['pair', 'literal', 'unpaired', 'support']) errors[key].push(...files.errors[key]);
}

const line = (label, list) => console.log(`${list.length} ${label}`);
console.log(`rename-check — fatia ${slice}: ${info.files} arquivos Java pareados, ${info.tokens} tokens alinhados`);
line('pares fora do mapa', errors.pair);
line('literais de texto alterados', errors.literal);
line('arquivos sem par', errors.unpaired);
line('colisões', errors.collision);
line('outras divergências (tokens, tipo de token, imports, retido alterado)', errors.support);
console.log(`(informativo) ${info.pairs.size} pares distintos aplicados, ${info.singleWord} literais de uma palavra pelo mapa, ${info.comments} comentários alterados`);
if (info.codeLiterals.size) console.log(`(informativo) literais Java que são código, por classe: ${[...info.codeLiterals].sort().map(([kind, count]) => `${kind}: ${count}`).join(', ')}`);
if (info.nonJava) console.log(`(informativo) ${info.nonJava.files} arquivos que não são Java pareados (${[...info.nonJava.byMode].map(([mode, count]) => `${mode}: ${count}`).join(', ')}), ${info.nonJava.tokens} tokens alinhados`);
console.log(`(informativo) ${info.acceptedCollisions} colisões aceitas em explicit-pairs.json (revisadas, com motivo)`);
console.log(`(informativo) ${info.manual.length} edições manuais de literal permitidas em explicit-pairs.json`);
if (verbose || [errors.pair, errors.literal, errors.unpaired, errors.collision, errors.support].some((list) => list.length)) {
  for (const [title, list] of Object.entries(errors)) {
    if (list.length) console.log(`\n== ${title} ==\n${list.slice(0, verbose ? list.length : 40).join('\n')}${!verbose && list.length > 40 ? `\n… +${list.length - 40}` : ''}`);
  }
}
if (verbose) for (const manual of info.manual) console.log(`manual: ${manual.file}:${manual.line} ${JSON.stringify(manual.from)} → ${JSON.stringify(manual.to)} (${manual.reason})`);
process.exit(Object.values(errors).some((list) => list.length) ? 1 : 0);
