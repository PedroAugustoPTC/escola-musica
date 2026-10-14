#!/usr/bin/env node
// Aplica as edições de texto exato de `explicit-pairs.json` → `fileEdits` a arquivos que não são Java (poms,
// Dockerfile, scripts, compose). Cada edição troca `from` por `to` e DEVE casar exatamente `count` vezes na árvore-base.
// O script é idempotente: se o texto novo já está no arquivo com a mesma contagem, a edição conta como já aplicada.
// Qualquer outra contagem falha sem gravar nada. O `rename-check` aplica as mesmas trocas à árvore-base antes de comparar.
//
//   node apply-file-edits.mjs [--root <raiz>] [--check]
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const args = process.argv.slice(2);
const root = resolve(args.includes('--root') ? args[args.indexOf('--root') + 1] : process.cwd());
const check = args.includes('--check');
const explicit = JSON.parse(readFileSync(join(root, 'specs/003-migrate-code-to-english/tools/explicit-pairs.json'), 'utf8'));
const count = (text, part) => (part ? text.split(part).length - 1 : 0);
const problems = [];
const pending = [];
let applied = 0;
let already = 0;
for (const [path, edits] of Object.entries(explicit.fileEdits ?? {})) {
  const file = join(root, path);
  if (!existsSync(file)) {
    problems.push(`${path}: arquivo inexistente`);
    continue;
  }
  let text = readFileSync(file, 'utf8');
  for (const edit of edits) {
    const found = count(text, edit.from);
    if (found === edit.count) {
      text = text.split(edit.from).join(edit.to);
      applied++;
    } else if (found === 0 && (edit.to === '' || count(text, edit.to) >= edit.count)) {
      already++; // já aplicada (idempotência)
    } else {
      problems.push(`${path}: "${edit.from.slice(0, 60).replace(/\n/g, '⏎')}" casa ${found} vez(es), esperado ${edit.count}`);
    }
  }
  pending.push([file, text]);
}
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
if (!check) for (const [file, text] of pending) writeFileSync(file, text);
console.log(`edições de arquivo ${check ? 'conferidas' : 'aplicadas'}: ${applied} novas, ${already} já aplicadas`);
