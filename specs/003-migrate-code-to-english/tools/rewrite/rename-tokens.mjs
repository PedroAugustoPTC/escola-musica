#!/usr/bin/env node
// Renomeia, por arquivo, TODOS os identificadores Java `from` → `to` (tokens do lexer; literais e comentários ficam). Serve
// aos identificadores que o guardião achou depois da receita e que não estavam no mapa (resíduos de português em nomes
// que parecem inglês, como `divide`). Cada par vem de `explicit-pairs.json` → `identifiers` (o rename-check o aceita
// por ali) e a lista de arquivos de `residualRenames`: [{ files: [sufixos], from, to }].
//
//   node rename-tokens.mjs [--root <raiz>]
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import { lex } from '../lib/java-lexer.mjs';

const root = resolve(process.argv.includes('--root') ? process.argv[process.argv.indexOf('--root') + 1] : process.cwd());
const explicit = JSON.parse(readFileSync(join(root, 'specs/003-migrate-code-to-english/tools/explicit-pairs.json'), 'utf8'));
const files = [];
(function walk(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.name === 'target') continue;
    const full = join(directory, entry.name);
    if (entry.isDirectory()) walk(full);
    else if (entry.name.endsWith('.java')) files.push(full);
  }
})(join(root, 'backend'));
let total = 0;
for (const rename of explicit.residualRenames ?? []) {
  if (explicit.identifiers?.[rename.base ?? rename.from] !== rename.to) throw new Error(`"${rename.base ?? rename.from}" precisa estar em explicit-pairs.json → identifiers`);
  for (const suffix of rename.files) {
    const matches = files.filter((file) => relative(root, file).split(sep).join('/').endsWith(suffix));
    if (matches.length !== 1) throw new Error(`"${suffix}" casa ${matches.length} arquivos`);
    const source = readFileSync(matches[0], 'utf8');
    const tokens = lex(source).tokens.filter((token) => token.type === 'ident' && token.value === rename.from);
    if (!tokens.length) continue; // já aplicado
    let text = source;
    for (const token of [...tokens].sort((a, b) => b.offset - a.offset)) text = text.slice(0, token.offset) + rename.to + text.slice(token.offset + token.value.length);
    writeFileSync(matches[0], text);
    total += tokens.length;
  }
}
console.log(`${total} identificadores renomeados`);
