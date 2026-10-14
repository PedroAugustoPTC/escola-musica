#!/usr/bin/env node
// Editor de literais Java que são código (T065, T066, T070, T076, T077): troca, pelo mapa de identificadores, só o
// que `lib/literal-classes.mjs` classifica como código (consulta, nome de tabela/coluna, chave de configuração, rótulo
// de coluna, atribuição de propriedade) e as edições listadas em `explicit-pairs.json` → `literals` com `slice: 3`.
// Texto para pessoas, chave de JSON do fio e valor de enumeração nunca são tocados.
//
//   node edit-literals.mjs --slice 3 [--root <raiz>] [--dir backend/application] [--apply] [--list <classe>]
//
// Sem `--apply` só relata (por classe, com o antes e o depois). Com `--apply` grava os arquivos e escreve
// `tools/rewrite/literal-edits-slice-<N>.json` (evidência: arquivo, linha, classe, antes, depois).
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import { lex } from '../lib/java-lexer.mjs';
import { classifyLiterals, literalBody, transformLiteral } from '../lib/literal-classes.mjs';
import { loadRenameMap } from '../lib/rename-map.mjs';

const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const slice = Number(option('--slice', '3'));
const root = resolve(option('--root', process.cwd()));
const dirs = option('--dir', 'backend/application').split(',');
const apply = args.includes('--apply');
const listClass = option('--list');
const map = loadRenameMap(root, slice);

function* javaFiles(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.name === 'target') continue;
    const full = join(directory, entry.name);
    if (entry.isDirectory()) yield* javaFiles(full);
    else if (entry.name.endsWith('.java')) yield full;
  }
}

const manual = (map.explicit.literals ?? []).filter((entry) => entry.slice === slice);
const edits = [];
const unmatched = new Set(manual.map((entry) => entry));

for (const dir of dirs) {
  for (const file of javaFiles(join(root, dir, 'src'))) {
    const path = relative(root, file).split(sep).join('/');
    const source = readFileSync(file, 'utf8');
    const { tokens } = lex(source);
    const excluded = (map.explicit.literalEditExclusions ?? []).some((entry) => path.endsWith(entry.file));
    const classes = excluded ? new Map() : classifyLiterals(tokens, { envNames: new Set(map.explicit.envNames ?? []), path, slice, keep: map.explicit.slice4?.keepLiterals ?? [], typedWords: map.explicit.slice4?.typedWords ?? [], typedWordFiles: map.explicit.slice4?.typedWordFiles ?? [], callbackActions: Object.keys(map.explicit.slice4?.callbackActions ?? {}), mainCallbackDirs: map.explicit.slice4?.mainCallbackDirs ?? [], callbackAmbiguous: map.explicit.slice4?.callbackAmbiguous ?? [] });
    const replacements = []; // { start, end, text, kind, line, from, to }
    const occurrences = new Map();
    tokens.forEach((token, index) => {
      if (token.type !== 'string' && token.type !== 'textblock') return;
      const body = literalBody(token);
      const opener = token.type === 'textblock' ? 3 : 1;
      const start = token.offset + opener;
      const key = `${path}\u0000${body}`;
      const nth = (occurrences.get(key) ?? 0) + 1;
      occurrences.set(key, nth);
      const entry = manual.find((candidate) => path.endsWith(candidate.file) && candidate.from === body && (!candidate.occurrence || candidate.occurrence === nth));
      let kind = classes.get(index);
      let to;
      if (entry) {
        kind = 'manual';
        to = entry.to;
        unmatched.delete(entry);
      } else if (kind) to = transformLiteral(kind, body, map);
      if (to === undefined || to === body) return;
      replacements.push({ start, end: start + body.length, text: to, kind, line: token.line, from: body, to });
    });
    if (!replacements.length) continue;
    for (const replacement of replacements) edits.push({ file: path, line: replacement.line, kind: replacement.kind, from: replacement.from, to: replacement.to });
    if (apply) {
      let text = source;
      for (const replacement of [...replacements].sort((a, b) => b.start - a.start)) text = text.slice(0, replacement.start) + replacement.text + text.slice(replacement.end);
      writeFileSync(file, text);
    }
  }
}

const byKind = new Map();
for (const edit of edits) byKind.set(edit.kind, (byKind.get(edit.kind) ?? 0) + 1);
console.log(`${apply ? 'aplicadas' : 'candidatas'}: ${edits.length} edições de literal em ${new Set(edits.map((edit) => edit.file)).size} arquivos`);
for (const [kind, count] of [...byKind].sort()) console.log(`  ${kind}: ${count}`);
if (unmatched.size) {
  console.log(`\n${unmatched.size} edição(ões) manual(is) de explicit-pairs não encontradas (já aplicadas ou erro):`);
  for (const entry of unmatched) console.log(`  ${entry.file}: ${JSON.stringify(entry.from)}`);
}
if (listClass) {
  for (const edit of edits.filter((candidate) => candidate.kind === listClass)) console.log(`${edit.file.split('/').at(-1)}:${edit.line}  ${JSON.stringify(edit.from).slice(0, 120)}  →  ${JSON.stringify(edit.to).slice(0, 120)}`);
}
if (apply) {
  // a evidência é cumulativa: uma segunda passada (edições novas) acrescenta, não apaga as da primeira
  const evidence = join(root, `specs/003-migrate-code-to-english/tools/rewrite/literal-edits-slice-${slice}.json`);
  const previous = existsSync(evidence) && !args.includes('--fresh') ? JSON.parse(readFileSync(evidence, 'utf8')) : [];
  writeFileSync(evidence, `${JSON.stringify([...previous, ...edits], null, 2)}\n`);
}
