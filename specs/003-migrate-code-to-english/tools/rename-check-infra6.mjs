#!/usr/bin/env node
// Verificador da fatia 6 (task T188): infra, scripts, workflows e os poucos arquivos de fora que carregam os mesmos nomes.
//
//   node rename-check-infra6.mjs [--root <raiz>] [--base <commit>] [--verbose] [--out <arquivo>]
//
// Para cada arquivo do escopo (`tools/lib/slice6-rules.mjs`) compara a versão do commit-base (git show) com a do
// diretório de trabalho, palavra por palavra (LCS sobre `[A-Za-z0-9]+` e sinais). Todo trecho que mudou (removidas R →
// acrescentadas A) tem de ser explicado pelas trocas permitidas: traduzir R pela tabela de frases tem de dar A. Reprova
// por:
//   - trecho fora da tabela (palavra trocada sem regra, ou texto para pessoas reescrito);
//   - arquivo do escopo sem par (ausente no commit-base ou no diretório de trabalho);
//   - arquivo movido (JSON de carga) com bytes diferentes;
//   - arquivo do escopo sem nenhuma troca (escopo morto).
// Lista à parte, só como informação, os trechos que caíram em comentário ou em texto (frase com espaço): são frases em
// português em que só um nome (comando, arquivo, serviço) mudou, e devem ser conferidas por quem revisa.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const root = resolve(option('--root', process.cwd()));
const base = option('--base', 'HEAD');
const verbose = args.includes('--verbose');
const outPath = option('--out', null);
const here = dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const { moves, scope, manualFiles, rules, reviewed } = await import(pathToFileURL(join(here, 'lib/slice6-rules.mjs')).href);

// um identificador com hífen ou sublinhado internos (`testes-backend`, `VPS_HOST_CONHECIDO`) é um token só
const TOKEN = /[A-Za-z0-9]+(?:[-_][A-Za-z0-9]+)*|[^A-Za-z0-9\s]/g;
const tokenize = (text) => {
  const tokens = [];
  const lines = text.split('\n');
  lines.forEach((line, index) => {
    for (const match of line.matchAll(TOKEN)) tokens.push({ text: match[0], line: index + 1 });
  });
  return tokens;
};
const words = (text) => (text.match(TOKEN) ?? []);

/** LCS de duas listas de texto; devolve os trechos que diferem como { removed, added, start }. */
function diff(left, right) {
  let prefix = 0;
  while (prefix < left.length && prefix < right.length && left[prefix] === right[prefix]) prefix += 1;
  let suffix = 0;
  while (suffix < left.length - prefix && suffix < right.length - prefix && left[left.length - 1 - suffix] === right[right.length - 1 - suffix]) suffix += 1;
  const a = left.slice(prefix, left.length - suffix);
  const b = right.slice(prefix, right.length - suffix);
  const n = a.length;
  const m = b.length;
  const table = Array.from({ length: n + 1 }, () => new Uint16Array(m + 1));
  for (let i = n - 1; i >= 0; i -= 1) for (let j = m - 1; j >= 0; j -= 1) table[i][j] = a[i] === b[j] ? table[i + 1][j + 1] + 1 : Math.max(table[i + 1][j], table[i][j + 1]);
  const hunks = [];
  let i = 0;
  let j = 0;
  let current = null;
  const flush = () => {
    if (current) hunks.push(current);
    current = null;
  };
  while (i < n || j < m) {
    if (i < n && j < m && a[i] === b[j]) {
      flush();
      i += 1;
      j += 1;
    } else if (j < m && (i === n || table[i][j + 1] >= table[i + 1][j])) {
      current ??= { removed: [], added: [], start: prefix + i };
      current.added.push(j + prefix);
      j += 1;
    } else {
      current ??= { removed: [], added: [], start: prefix + i };
      current.removed.push(i + prefix);
      i += 1;
    }
  }
  flush();
  return hunks;
}

// tabela de frases: tokens antigos → conjuntos de tokens novos permitidos, extraída dos pares das regras (cada par
// vira os seus trechos mínimos). Uma palavra pode ter mais de um destino (`massa` → `demo-data`, `demo_data`…).
const phrases = new Map(); // "arquivo|tokens antigos" → Set("tokens novos")
const additions = new Map(); // arquivo → trechos acrescentados e revisados
const maxPhrase = { value: 1 };
const addPhrase = (from, to, file) => {
  const key = `${file ?? '*'}|${from.join(' ')}`;
  if (!phrases.has(key)) phrases.set(key, new Set());
  phrases.get(key).add(to.join(' '));
  maxPhrase.value = Math.max(maxPhrase.value, from.length);
};
const register = (oldText, newText, file = null) => {
  const left = words(oldText);
  const right = words(newText);
  if (left.length === 0) {
    if (!additions.has(file)) additions.set(file, new Set());
    additions.get(file).add(right.join(' '));
    return;
  }
  for (const hunk of diff(left, right)) addPhrase(hunk.removed.map((index) => left[index]), hunk.added.map((index) => right[index]), file);
};
for (const rule of rules) for (const [from, to] of rule.pairs) register(from, to);
for (const item of reviewed) register(item.from, item.to, item.file);

/** Os tokens acrescentados saem dos removidos aplicando só frases da tabela (ou mantendo o token)? */
function explained(removed, added, file) {
  const memo = new Map();
  const walk = (i, j) => {
    if (i === removed.length) return j === added.length;
    const key = `${i}:${j}`;
    if (memo.has(key)) return memo.get(key);
    let ok = false;
    if (j < added.length && removed[i] === added[j] && walk(i + 1, j + 1)) ok = true;
    for (let size = Math.min(maxPhrase.value, removed.length - i); size >= 1 && !ok; size -= 1) {
      const from = removed.slice(i, i + size).join(' ');
      for (const owner of [file, '*']) {
        for (const target of phrases.get(`${owner}|${from}`) ?? []) {
          const to = target === '' ? [] : target.split(' ');
          if (to.every((token, offset) => added[j + offset] === token) && walk(i + size, j + to.length)) ok = true;
        }
      }
    }
    memo.set(key, ok);
    return ok;
  };
  return walk(0, 0);
}

const git = (...gitArgs) => execFileSync('git', gitArgs, { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
const baseOf = new Map(moves.map(([from, to]) => [to, from]));
const checked = [...scope.infra, ...scope.outside, ...new Set(reviewed.map((item) => item.file))].filter((file, index, all) => all.indexOf(file) === index);

const problems = [];
const informational = [];
let tokenChanges = 0;
let filesCompared = 0;
let filesChanged = 0;

for (const file of checked) {
  const basePath = baseOf.get(file) ?? file;
  let before;
  try {
    before = git('show', `${base}:${basePath}`);
  } catch {
    problems.push(`${file}: sem par no commit-base (${base}:${basePath})`);
    continue;
  }
  if (!existsSync(join(root, file))) {
    problems.push(`${file}: ausente no diretório de trabalho`);
    continue;
  }
  const after = readFileSync(join(root, file), 'utf8');
  filesCompared += 1;
  if (before === after) {
    if (scope.infra.includes(file) || scope.outside.includes(file)) problems.push(`${file}: escopo sem nenhuma troca`);
    continue;
  }
  filesChanged += 1;
  const left = tokenize(before);
  const right = tokenize(after);
  const afterLines = after.split('\n');
  for (const hunk of diff(left.map((t) => t.text), right.map((t) => t.text))) {
    const removed = hunk.removed.map((index) => left[index].text);
    const added = hunk.added.map((index) => right[index].text);
    const line = (hunk.added.length ? right[hunk.added[0]] : left[hunk.removed[0]]).line;
    const ok = removed.length === 0 ? Boolean(additions.get(file)?.has(added.join(' '))) : explained(removed, added, file);
    if (ok) {
      tokenChanges += 1;
      const text = (afterLines[line - 1] ?? '').trim();
      if (/^(#|\/\/|\*|\/\*)/.test(text) || /"[^"]* [^"]*"/.test(text) || /^[-+*]\s|^\|/.test(text)) informational.push(`${file}:${line}: ${removed.join(' ')} → ${added.join(' ')}   | ${text.slice(0, 120)}`);
    } else {
      problems.push(`${file}:${line}: trecho fora da tabela — removido [${removed.join(' ')}] acrescentado [${added.join(' ')}] fora das frases da tabela`);
    }
  }
}

// arquivos movidos sem tradução de conteúdo (JSON de carga): bytes idênticos
let movedIdentical = 0;
for (const [from, to] of moves) {
  if (scope.infra.includes(to) || scope.outside.includes(to)) continue;
  try {
    const before = execFileSync('git', ['show', `${base}:${from}`], { cwd: root, maxBuffer: 64 * 1024 * 1024 });
    const after = readFileSync(join(root, to));
    if (Buffer.compare(before, after) === 0) movedIdentical += 1;
    else problems.push(`${to}: bytes diferentes de ${from}`);
  } catch {
    problems.push(`${to}: sem par para comparar com ${from}`);
  }
}

// regras de renomeação pendentes: o nome antigo não pode sobrar nos arquivos do escopo
const lines = [];
lines.push(`rename-check — fatia 6: ${filesCompared} arquivos pareados, ${filesChanged} com troca, ${tokenChanges} trechos explicados pela tabela, ${movedIdentical} arquivos movidos idênticos`);
lines.push(`arquivos de operação revisados à mão (fora desta verificação): ${manualFiles.join(', ')}`);
lines.push(`trechos fora da tabela / arquivos sem par / escopo morto: ${problems.length}`);
if (problems.length) lines.push(...problems.map((problem) => `  ${problem}`));
lines.push(`trechos em comentário ou texto (só nomes mudaram; conferir): ${informational.length}`);
if (verbose) lines.push(...informational.map((item) => `  ${item}`));
const report = `${lines.join('\n')}\n`;
process.stdout.write(report);
if (outPath) writeFileSync(resolve(root, outPath), report);
process.exit(problems.length ? 1 : 0);
