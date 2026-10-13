#!/usr/bin/env node
// Troca, num documento Markdown, só os IDENTIFICADORES pelo mapa da fatia (R-06): trechos de código em linha
// (`assim`) e blocos cercados. A prosa nunca é tocada (a documentação fica em português).
//
//   node doc-identifiers.mjs --slice 2 <arquivo.md> [--check]
//
// Cada palavra de identificador dentro do código que é chave do mapa vira o valor do mapa; o resto fica igual.
// `--skip a,b` ignora palavras que são nome de coisa de outra fatia (ex.: o volume `documentos`).
// Imprime cada troca (linha, antigo → novo) para conferência rápida. `--check` não grava.
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { loadRenameMap } from './lib/rename-map.mjs';

const args = process.argv.slice(2);
const slice = Number(args[args.indexOf('--slice') + 1]);
const check = args.includes('--check');
const skip = new Set((args.includes('--skip') ? args[args.indexOf('--skip') + 1] : '').split(',').filter(Boolean));
const file = args.find((arg, index) => !arg.startsWith('--') && args[index - 1] !== '--slice' && args[index - 1] !== '--skip');
if (!file || !slice) {
  console.error('Uso: doc-identifiers.mjs --slice <N> <arquivo.md> [--check]');
  process.exit(2);
}
const map = loadRenameMap(process.cwd(), slice);
const path = resolve(file);
const lines = readFileSync(path, 'utf8').split('\n');
const changes = [];
let fenced = false;

/**
 * Troca as palavras de identificador de um trecho de código, respeitando a retenção da fatia: constantes em
 * SCREAMING_CASE, nomes retidos (B1/B2), chaves literais (B3) e partes de caminho (ao lado de "-" ou "/") ficam.
 */
const rewrite = (code, lineNumber) =>
  code.replace(/[A-Za-z_$][A-Za-z0-9_$]*/g, (word, offset) => {
    const novo = map.ident.get(word);
    if (!novo || novo === word || skip.has(word)) return word;
    if (/^[A-Z][A-Z0-9_]+$/.test(word) || map.heldNames.has(word) || Object.hasOwn(map.held.B3 ?? {}, word)) return word;
    const before = code[offset - 1];
    const after = code[offset + word.length];
    if (before === '-' || before === '/' || after === '-' || after === '/') return word;
    changes.push(`${lineNumber}: ${word} → ${novo}`);
    return novo;
  });

const output = lines.map((line, index) => {
  if (/^\s*```/.test(line)) {
    fenced = !fenced;
    return line;
  }
  if (fenced) {
    // comentário de linha (`//`) é prosa: fica em português
    const cut = line.indexOf('//');
    return cut < 0 ? rewrite(line, index + 1) : rewrite(line.slice(0, cut), index + 1) + line.slice(cut);
  }
  return line.replace(/`([^`\n]+)`/g, (_, code) => `\`${rewrite(code, index + 1)}\``);
});

if (!check) writeFileSync(path, output.join('\n'));
console.log(`${changes.length} trocas em ${file}${check ? ' (simulação)' : ''}`);
for (const change of changes) console.log(`  ${change}`);
