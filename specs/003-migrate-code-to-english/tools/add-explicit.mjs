#!/usr/bin/env node
// Acrescenta entradas a uma seção de explicit-pairs.json sem duplicar (usa JSON na entrada padrão).
//   echo '[{"file":"…","from":"…","to":"…","slice":4,"reason":"…"}]' | node add-explicit.mjs literals
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const section = process.argv[2];
const path = join(dirname(fileURLToPath(import.meta.url)), 'explicit-pairs.json');
const pairs = JSON.parse(readFileSync(path, 'utf8'));
const entries = JSON.parse(readFileSync(0, 'utf8'));
const list = (pairs[section] ??= []);
let added = 0;
for (const entry of entries) {
  if (list.some((existing) => JSON.stringify(existing) === JSON.stringify(entry))) continue;
  list.push(entry);
  added++;
}
writeFileSync(path, `${JSON.stringify(pairs, null, 2)}\n`);
console.log(`${section}: ${added} entrada(s) acrescentada(s), ${list.length} no total`);
