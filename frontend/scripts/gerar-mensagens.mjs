// Gera src/mensagens/mensagens.json a partir do catálogo único do backend (FR-003, R-18).
// Formato do .properties: CODIGO=texto em uma linha; linhas vazias e comentários (#, !) ignorados.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const origem = resolve(
  aqui,
  '../../backend/application/src/main/resources/messages/messages_pt_BR.properties',
);
const destino = resolve(aqui, '../src/mensagens/mensagens.json');

const mensagens = {};
for (const linha of readFileSync(origem, 'utf8').split(/\r?\n/)) {
  const texto = linha.trim();
  if (!texto || texto.startsWith('#') || texto.startsWith('!')) continue;
  const separador = texto.indexOf('=');
  if (separador < 1) continue;
  mensagens[texto.slice(0, separador).trim()] = texto.slice(separador + 1).trim();
}

mkdirSync(dirname(destino), { recursive: true });
writeFileSync(destino, JSON.stringify(mensagens, null, 2) + '\n', 'utf8');
console.log(`mensagens.json: ${Object.keys(mensagens).length} textos`);
