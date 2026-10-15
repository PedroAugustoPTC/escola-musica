// Gera src/messages/messages.json a partir do catálogo único do backend (FR-003, R-18).
// Formato do .properties: CODIGO=texto em uma linha; linhas vazias e comentários (#, !) ignorados.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const source = resolve(
  here,
  '../../backend/application/src/main/resources/messages/messages_pt_BR.properties',
);
const target = resolve(here, '../src/messages/messages.json');

const messages = {};
for (const line of readFileSync(source, 'utf8').split(/\r?\n/)) {
  const text = line.trim();
  if (!text || text.startsWith('#') || text.startsWith('!')) continue;
  const separator = text.indexOf('=');
  if (separator < 1) continue;
  messages[text.slice(0, separator).trim()] = text.slice(separator + 1).trim();
}

mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, JSON.stringify(messages, null, 2) + '\n', 'utf8');
console.log(`messages.json: ${Object.keys(messages).length} textos`);
