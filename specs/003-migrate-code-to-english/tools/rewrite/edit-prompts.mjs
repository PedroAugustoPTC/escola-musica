#!/usr/bin/env node
// Prompts do LLM (T073): troca SÓ os tokens que são nome de campo do JSON (esquemas de IA e entrada enviada ao LLM);
// as instruções em português ficam idênticas byte a byte (FR-033). O conjunto é fechado: os nomes dos componentes de
// `record` de `held-back/slice-2.json` (B2). Um nome só é campo quando aparece em uma destas formas:
//   "nome"                  entre aspas duplas
//   - nome: …               chave de item de lista (início de linha, antes de dois-pontos)
//   com as chaves: a, b, c  lista de chaves após "chaves:" (separadas por vírgula, até o ponto final)
//   { a, b[], c }           lista entre chaves de um item de seção
//   (a, b, c ou d)          enumeração entre parênteses que tem ao menos um nome camelCase do conjunto
//   nomeComposto            camelCase com maiúscula no meio (não é palavra do português)
// Tudo o mais é prosa e não muda, e as palavras do conjunto que ficam sem troca são listadas para revisão.
//
//   node edit-prompts.mjs --slice 3 <arquivo>... [--apply] [--root <raiz>]
import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { loadRenameMap } from '../lib/rename-map.mjs';

const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const slice = Number(option('--slice', '3'));
const root = resolve(option('--root', process.cwd()));
const apply = args.includes('--apply');
const files = args.filter((arg, index) => !arg.startsWith('--') && !['--slice', '--root'].includes(args[index - 1]));
const map = loadRenameMap(root, slice);
const heldSlice2 = JSON.parse(readFileSync(join(root, 'specs/003-migrate-code-to-english/tools/held-back/slice-2.json'), 'utf8'));
const fields = new Set();
for (const entry of Object.values(heldSlice2.B2)) for (const member of entry.members) if (map.ident.has(member)) fields.add(member);
const next = (name) => map.ident.get(name) ?? name;
const isCamel = (word) => /[a-z][A-Z]/.test(word);

let total = 0;
let leftover = 0;
for (const arg of files) {
  const file = resolve(root, arg);
  const source = readFileSync(file, 'utf8');
  const lines = source.split('\n');
  const out = [];
  lines.forEach((original, index) => {
    let line = original;
    const changes = [];
    const swap = (word, kind) => {
      if (!fields.has(word)) return word;
      changes.push(`${word} → ${next(word)} (${kind})`);
      return next(word);
    };
    // lista de chaves: tudo entre "chaves:" e o ponto final da linha
    line = line.replace(/(chaves:\s*)([^.\n]*)/g, (_, prefix, list) => prefix + list.split(/(,\s*)/).map((part) => (/^[A-Za-z]+$/.test(part) ? swap(part, 'lista de chaves') : part)).join(''));
    // lista entre chaves { a, b[], c }
    line = line.replace(/\{([^{}]*)\}/g, (whole, list) => `{${list.split(/(,\s*)/).map((part) => part.replace(/^(\s*)([A-Za-z]+)(\[\])?(\s*)$/, (_, lead, word, brackets = '', tail) => `${lead}${swap(word, 'lista entre chaves')}${brackets}${tail}`)).join('')}}`);
    // enumeração entre parênteses com algum nome camelCase do conjunto
    line = line.replace(/\(([^()]*)\)/g, (whole, list) => {
      const parts = list.split(/(,\s*|\s+ou\s+)/);
      const words = parts.filter((part, position) => position % 2 === 0);
      if (!words.some((word) => isCamel(word) && fields.has(word)) || !words.every((word) => /^[A-Za-z]+$/.test(word))) return whole;
      return `(${parts.map((part, position) => (position % 2 === 0 ? swap(part, 'enumeração') : part)).join('')})`;
    });
    // entre aspas duplas
    line = line.replace(/"([A-Za-z]+)"/g, (whole, word) => (fields.has(word) ? `"${swap(word, 'entre aspas')}"` : whole));
    // chave de item de lista
    line = line.replace(/^(\s*[-•]\s*)([A-Za-z]+)(:)/, (_, lead, word, colon) => `${lead}${swap(word, 'item de lista')}${colon}`);
    // camelCase solto
    line = line.replace(/\b[a-z]+(?:[A-Z][a-z0-9]*)+\b/g, (word) => (isCamel(word) ? swap(word, 'camelCase') : word));
    if (line !== original) {
      total += changes.length;
      console.log(`${arg}:${index + 1}  ${changes.join('; ')}`);
    }
    // palavras do conjunto que ficaram na linha sem troca (prosa, a conferir)
    for (const word of line.match(/[A-Za-z]+/g) ?? []) {
      if (fields.has(word) && next(word) !== word && !changes.some((change) => change.startsWith(`${word} →`))) {
        leftover++;
        console.log(`  (prosa, não trocada) ${arg}:${index + 1}  "${word}"`);
      }
    }
    out.push(line);
  });
  if (apply) writeFileSync(file, out.join('\n'));
}
console.log(`${apply ? 'aplicadas' : 'candidatas'}: ${total} trocas de campo em ${files.length} arquivo(s); ${leftover} palavra(s) do conjunto mantida(s) como prosa`);
