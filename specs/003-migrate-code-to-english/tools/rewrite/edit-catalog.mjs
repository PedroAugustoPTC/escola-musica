#!/usr/bin/env node
// Troca, no catálogo de mensagens, só as CHAVES e os MARCADORES `{nome}` pelo mapa da fatia (T120). O texto é intocado.
//
//   node edit-catalog.mjs --slice 4 [--apply] <messages_pt_BR.properties>
//
// Também grava `tools/message-key-map.json` e `tools/message-marker-map.json` (chaves e marcadores antigo → novo), que o
// `rename-check --catalog` usa para provar que o texto não mudou.
import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { loadRenameMap } from '../lib/rename-map.mjs';

const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const slice = Number(option('--slice', '4'));
const apply = args.includes('--apply');
const files = args.filter((arg, index) => !arg.startsWith('--') && args[index - 1] !== '--slice');
const root = process.cwd();
const map = loadRenameMap(root, slice);
const keyMap = {};
const markerMap = {};
for (const path of files) {
  const lines = readFileSync(resolve(path), 'utf8').split('\n');
  const out = lines.map((line) => {
    if (/^\s*[#!]/.test(line) || !line.trim()) return line;
    const at = line.indexOf('=');
    // linha de continuação (sem chave): só marcadores
    const hasKey = at > 0 && /^[A-Z][A-Z0-9_]*$/.test(line.slice(0, at));
    let key = hasKey ? line.slice(0, at) : null;
    let value = hasKey ? line.slice(at + 1) : line;
    if (key) {
      const novo = map.ident.get(key) ?? key;
      if (novo !== key) keyMap[key] = novo;
      key = novo;
    }
    value = value.replace(/\{([A-Za-z_][A-Za-z0-9_]*)\}/g, (all, name) => {
      const novo = map.ident.get(name) ?? name;
      if (novo !== name) markerMap[name] = novo;
      return `{${novo}}`;
    });
    return key ? `${key}=${value}` : value;
  });
  if (apply) writeFileSync(resolve(path), out.join('\n'));
}
const tools = join(root, 'specs/003-migrate-code-to-english/tools');
if (apply) {
  writeFileSync(join(tools, 'message-key-map.json'), `${JSON.stringify(keyMap, null, 2)}\n`);
  writeFileSync(join(tools, 'message-marker-map.json'), `${JSON.stringify(markerMap, null, 2)}\n`);
}
console.log(`${apply ? 'aplicado' : 'candidato'}: ${Object.keys(keyMap).length} chaves e ${Object.keys(markerMap).length} marcadores`);
const collisions = new Map();
for (const [antigo, novo] of Object.entries(keyMap)) collisions.set(novo, [...(collisions.get(novo) ?? []), antigo]);
for (const [novo, antigos] of collisions) if (antigos.length > 1) console.log(`COLISÃO de chave: ${antigos.join(', ')} → ${novo}`);
