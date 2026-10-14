#!/usr/bin/env node
// Verificador do frontend na fatia 4 (tasks T144): só NOMES DE FIO mudaram.
//
//   node rename-check-frontend.mjs <árvore-base> <árvore-de-trabalho>
//
// Compara, palavra a palavra (git diff --word-diff), tudo em frontend/src e frontend/tests, menos os arquivos gerados
// (tipos da API e catálogo, conferidos por --contracts e --catalog) e o remendo manual do tema. Cada palavra trocada
// precisa estar no conjunto permitido (mapas de campos do contrato, de enumeração, de chaves e marcadores do catálogo).
// Contadores: palavras trocadas fora do mapa e trechos cuja contagem de palavras difere.
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const [baseRoot, workRoot] = process.argv.slice(2).filter((arg) => !arg.startsWith('--')).map((path) => resolve(path));
const read = (name) => JSON.parse(readFileSync(join(here, name), 'utf8'));

const allowed = new Map(); // antigo → Set(novos)
const allow = (from, to) => {
  if (typeof from !== 'string' || typeof to !== 'string' || from === to) return;
  if (!allowed.has(from)) allowed.set(from, new Set());
  allowed.get(from).add(to);
};
const walk = (node) => {
  if (!node || typeof node !== 'object') return;
  for (const [from, to] of Object.entries(node)) {
    if (typeof to === 'string') {
      allow(from, to);
      // caminhos e chaves compostas: alinha segmento a segmento
      const fromParts = from.split(/[/.]/);
      const toParts = to.split(/[/.]/);
      if (fromParts.length === toParts.length) fromParts.forEach((part, index) => allow(part, toParts[index]));
    } else walk(to);
  }
};
walk(read('api-field-map.json')['api-portal']);
walk(read('enum-pairs-slice-4.json'));
walk(read('message-key-map.json'));
walk(read('message-marker-map.json'));
// cabeças de chave montada em tempo de execução (`ETAPA_${etapa}`) e e-mails de exemplo dos testes e2e
for (const [from, to] of [['ETAPA_$', 'STEP_$'], ['TIPO_$', 'TYPE_$'], ['exemplo', 'example']]) allow(from, to);

const tokenRegex = '[A-Za-z0-9_$]+(-[A-Za-z0-9_$]+)*|[^[:space:]]';
// TemaProvedor.tsx: remendo manual revisado (chave e valores guardados do tema), registrado em evidence/slice-4.md
const skip = (path) => /api-tipos\.ts$|mensagens\.json$|app\/TemaProvedor\.tsx$/.test(path);

// `{ versao }` vira `{ version: versao }`: a chave nova entra e o valor segue o nome local
const targets = new Set([...allowed.values()].flatMap((set) => [...set]));
const shorthand = (word) => /^[A-Za-z0-9_]+:$/.test(word) && targets.has(word.slice(0, -1));
let expanded = 0;
const outside = [];
const uneven = [];
let swapped = 0;
let files = 0;
for (const folder of ['frontend/src', 'frontend/tests']) {
  let diff = '';
  try {
    diff = execFileSync('git', ['diff', '--no-index', '--word-diff=porcelain', `--word-diff-regex=${tokenRegex}`, '-U0', '--no-color', join(baseRoot, folder), join(workRoot, folder)], { encoding: 'utf8', maxBuffer: 1 << 29 });
  } catch (error) {
    diff = error.stdout ?? ''; // git diff --no-index sai com 1 quando há diferença
  }
  let file = '';
  let removed = [];
  let added = [];
  const flush = () => {
    if (removed.length || added.length) {
      if (removed.length === added.length) {
        removed.forEach((word, index) => {
          swapped++;
          if (!allowed.get(word)?.has(added[index])) outside.push(`${file}: "${word}" → "${added[index]}"`);
        });
      } else {
        // contagens diferentes: aceita só se cada palavra removida tiver par permitido entre as acrescentadas
        const rest = [...added];
        for (const word of removed) {
          const target = [...(allowed.get(word) ?? [])].find((candidate) => rest.includes(candidate));
          if (target) {
            rest.splice(rest.indexOf(target), 1);
            swapped++;
          } else uneven.push(`${file}: removido "${word}" sem par permitido em [${added.join(' ')}]`);
        }
        for (const word of rest) if (shorthand(word)) expanded++; else uneven.push(`${file}: acrescentado "${word}" sem palavra removida correspondente`);
      }
    }
    removed = [];
    added = [];
  };
  for (const text of diff.split('\n')) {
    if (text.startsWith('diff --git')) {
      flush();
      const match = /"?b\/([^"]+)"?$/.exec(text);
      file = (match ? match[1] : text).replaceAll('\\', '/');
      if (!skip(file)) files++;
      continue;
    }
    if (/^(\+\+\+ |--- |index |new file|deleted file)/.test(text)) continue;
    if (skip(file)) continue;
    if (text.startsWith('@@')) flush();
    else if (text === '~') continue;
    else if (text.startsWith('-')) removed.push(text.slice(1));
    else if (text.startsWith('+')) added.push(text.slice(1));
    else if (text.startsWith(' ')) flush();
  }
  flush();
}

console.log(`rename-check --frontend — fatia 4: ${files} arquivos comparados, ${swapped} palavras trocadas pelo mapa`);
console.log(`${outside.length} palavras trocadas fora do mapa`);
console.log(`${uneven.length} trechos com contagem de palavras diferente`);
console.log(`(informativo) ${expanded} propriedades abreviadas ganharam a chave nova (de { x } para { novaChave: x })`);
for (const [title, list] of [['fora do mapa', outside], ['contagem diferente', uneven]]) {
  if (list.length) console.log(`\n== ${title} ==\n${list.slice(0, 80).join('\n')}${list.length > 80 ? `\n… +${list.length - 80}` : ''}`);
}
process.exit(outside.length || uneven.length ? 1 : 0);
