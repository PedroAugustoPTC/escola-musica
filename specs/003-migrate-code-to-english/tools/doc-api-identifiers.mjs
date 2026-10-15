#!/usr/bin/env node
// Troca, num Markdown de contrato, só os identificadores DE API (fatia 4, T138 a T141): chamadas `GET /alunos/{id}`,
// códigos e chaves em CAIXA_ALTA, campos JSON e ações de botão. Rotas da SPA (`/entrar`, `/alunos/:alunoId`) e prosa ficam.
//
//   node doc-api-identifiers.mjs <arquivo.md> [--check]
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const read = (name) => JSON.parse(readFileSync(resolve(here, name), 'utf8'));
const all = read('api-field-map.json');
const api = all['api-portal'];
const actionsApi = read('api-field-map.json').actions;
const ident = new Map(read('maps/slice-4.json').map((item) => [item.antigo, item.novo]));
const explicit = read('explicit-pairs.json');
const callbackActions = explicit.slice4.callbackActions;
const segments = { ...api['path-segment'], ...all['telegram-channel-internal']['path-segment'], inicio: 'home' };
const properties = { ...api.property, ...actionsApi.property, ...all['telegram-channel-internal'].property };
const parameters = { ...api['path-parameter'], ...api.parameter, ...all['telegram-channel-internal']['path-parameter'], ...all['telegram-channel-internal'].parameter };
const schemas = { ...api.schema, ...actionsApi.schema, ...all['telegram-channel-internal'].schema };

const args = process.argv.slice(2);
const check = args.includes('--check');
const file = args.find((arg) => !arg.startsWith('--'));
const lines = readFileSync(resolve(file), 'utf8').split('\n');
let changes = 0;

function mapApiCall(code) {
  // GET /alunos/{id}/relatorio-em-aberto?busca=  ·  POST …/cancelamento
  return code.replace(/(\/|…\/)([A-Za-z0-9{}\-/]+)/g, (all, lead, rest) => lead + rest.replace(/[A-Za-z][A-Za-z0-9-]*/g, (word) => segments[word] ?? parameters[word] ?? word)).replace(/([?&])([A-Za-z]+)=/g, (all, sign, name) => `${sign}${parameters[name] ?? name}=`);
}
function mapSpan(code) {
  if (/.html|-system|.md|.json|.yaml|.sh/.test(code) && !/^(GET|POST) /.test(code)) return code; // caminhos de arquivo e de pasta

  if (/^(GET|POST|DELETE|PUT) /.test(code) || /^…\//.test(code)) return mapApiCall(code);
  if (/^v1:/.test(code)) return code.replace(/^v1:([a-z]+)/, (all, action) => `v1:${callbackActions[action] ?? action}`);
  if (/^\//.test(code) || /:alunoId|:id/.test(code)) return code; // rota da SPA: fatia 5
  if (/^\d{3}-/.test(code)) return code; // pasta de feature (001-…, 002-…, 003-…)
  if (/^[A-Z][A-Za-z]+$/.test(code) && Object.hasOwn(schemas, code)) return schemas[code]; // nome de esquema do contrato
  return code
    .replace(/\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*\b/g, (word) => ident.get(word) ?? word)
    .replace(/\b[a-z][A-Za-z0-9]*\b/g, (word) => (Object.hasOwn(properties, word) ? properties[word] : word))
    .replace(/\{([A-Za-z]+)\}/g, (all, marker) => `{${ident.get(marker) ?? marker}}`);
}

let fenced = false;
const out = lines.map((line, index) => {
  if (/^```/.test(line)) {
    fenced = !fenced;
    return line;
  }
  const mapper = (code) => {
    const novo = mapSpan(code);
    if (novo !== code) {
      changes++;
      if (check) console.log(`${index + 1}: ${code}  →  ${novo}`);
    }
    return novo;
  };
  if (fenced) return mapper(line);
  return line.replace(/`([^`]+)`/g, (all, code) => `\`${mapper(code)}\``);
});
if (!check) writeFileSync(resolve(file), out.join('\n'));
console.log(`${file}: ${changes} trechos de código ${check ? 'a trocar' : 'trocados'}`);
