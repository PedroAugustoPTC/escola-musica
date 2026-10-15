#!/usr/bin/env node
// Troca o nome das variáveis de ambiente (lista fechada `envNames` de `explicit-pairs.json`, identifier-map.md §6) dentro
// das LINHAS DE COMENTÁRIO (`# …`, inclusive exemplos comentados como `#NOME=valor`) de arquivos de configuração e de
// script. O texto do comentário continua em português; só o nome da variável que ele cita acompanha a renomeação.
//
//   node edit-env-comments.mjs <arquivo>... [--apply] [--root <raiz>]
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { loadRenameMap } from '../lib/rename-map.mjs';

const args = process.argv.slice(2);
const root = resolve(args.includes('--root') ? args[args.indexOf('--root') + 1] : process.cwd());
const apply = args.includes('--apply');
const files = args.filter((arg, index) => !arg.startsWith('--') && args[index - 1] !== '--root');
const map = loadRenameMap(root, 3);
const names = new Set(map.explicit.envNames ?? []);
let total = 0;
for (const arg of files) {
  const file = resolve(root, arg);
  const lines = readFileSync(file, 'utf8').split('\n');
  let changed = 0;
  const out = lines.map((line) => {
    const at = line.indexOf('#');
    if (at < 0) return line;
    const tail = line.slice(at).replace(/\b[A-Z][A-Z0-9_]*\b/g, (word) => (names.has(word) ? (map.ident.get(word) ?? word) : word));
    if (tail === line.slice(at)) return line;
    changed++;
    return line.slice(0, at) + tail;
  });
  total += changed;
  console.log(`${arg}: ${changed} linha(s) de comentário`);
  if (apply && changed) writeFileSync(file, out.join('\n'));
}
console.log(`${apply ? 'aplicadas' : 'candidatas'}: ${total} linha(s)`);
