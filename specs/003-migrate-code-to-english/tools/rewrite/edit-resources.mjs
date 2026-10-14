#!/usr/bin/env node
// Editor de recursos e configuração que não são Java (T066 a T074, T078): troca, pelo mapa de identificadores, só os
// IDENTIFICADORES de cada arquivo (chaves, variáveis de ambiente, classes CSS, expressões de modelo, chaves de JSON),
// nunca texto para pessoas, comentários nem valores. O mesmo tokenizador (`lib/simple-lexers.mjs`) é usado pelo
// `rename-check`, que depois prova token a token que mais nada mudou.
//
//   node edit-resources.mjs --slice 3 --profile config|env|ident <arquivo>... [--apply] [--strings] [--root <raiz>]
//
//   --profile config  configuração do Spring (`application*.yaml`): chaves, nome da variável em `${NOME:padrão}` e os
//                     valores de `configValues`; os padrões e os demais valores (segredos de teste, banco) NÃO mudam
//             env     variáveis de ambiente (compose, `.env.example`, shell, Dockerfile): só as da lista fechada
//                     `envNames` de `explicit-pairs.json` (identifier-map.md §6), em qualquer ponto fora de comentário,
//                     inclusive dentro de aspas e de `${…}`
//             ident   (padrão) todo identificador do arquivo (esquemas JSON, CSS, HTML de PDF)
//   --json-schema     (ident, JSON) também troca valor que cita propriedade do esquema (`required`, `$ref`)
//   --strings         (ident) também troca literal entre aspas que é identificador (uma palavra, composta) ou código embutido
//   Sem `--apply` só lista o que trocaria (arquivo:linha antes → depois).
// `explicit-pairs.json` → `keepTokens` lista o que NUNCA muda (valores retidos por B1, como `DIRETA`/`ORQUESTRADOR`).
import { readFileSync, writeFileSync } from 'node:fs';
import { relative, resolve, sep } from 'node:path';
import { looksLikeCode, mapEmbedded } from '../lib/align-simple.mjs';
import { isSingleWord } from '../lib/java-lexer.mjs';
import { loadRenameMap } from '../lib/rename-map.mjs';
import { isIdentifierWord, kindOf, lexSimple, unquote } from '../lib/simple-lexers.mjs';

const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const slice = Number(option('--slice', '3'));
const root = resolve(option('--root', process.cwd()));
const apply = args.includes('--apply');
const strings = args.includes('--strings');
const jsonSchema = args.includes('--json-schema');
const profile = option('--profile', 'ident');
const valueFlags = new Set(['--slice', '--root', '--profile']);
const files = args.filter((arg, index) => !arg.startsWith('--') && !valueFlags.has(args[index - 1]));
const map = loadRenameMap(root, slice);
const keep = new Set(map.explicit.keepTokens ?? []);
const configValues = map.explicit.configValues ?? {};
const UPPER = /^[A-Z][A-Z0-9_]*$/;
const envNames = new Set(map.explicit.envNames ?? []);

/** Nome novo de um identificador composto (chave pontilhada, kebab-case): o inteiro, ou parte a parte. */
function mapComposite(text) {
  if (keep.has(text)) return text;
  const cli = map.explicit.cliTerms?.[text]; // comandos do CLI: a lista fechada vence o mapa geral (`massa` → `demo-data`)
  if (cli) return cli;
  const whole = map.ident.get(text);
  if (whole) return whole;
  const parts = text.split(/([._-])/);
  if (parts.length === 1) return text;
  return parts.map((part, index) => (index % 2 === 0 ? map.mapIdent(part) : part)).join('');
}

/** `${...}` de modelo: troca as palavras dentro da expressão, mantendo o texto de fora. */
const mapTemplate = (text) => text.replace(/\$\{[^}]*\}/g, (expression) => mapEmbedded(expression, map));
/** Classes CSS de um atributo `class="a b"`: cada palavra pelo mapa. */
const mapClasses = (text) => text.split(/(\s+)/).map((part) => (/^\s+$/.test(part) ? part : mapComposite(part))).join('');
/** Troca só as variáveis de ambiente (`NOME_EM_MAIUSCULAS` do mapa) de um texto qualquer. */
const mapEnvWords = (text) => text.replace(/\b[A-Z][A-Z0-9_]*\b/g, (word) => (envNames.has(word) ? (map.ident.get(word) ?? word) : word));

function replacementsFor(kind, tokens) {
  const found = [];
  const schemaKeys = jsonSchema ? new Set(tokens.filter((token) => token.type === 'key').map((token) => unquote(token.value))) : null;
  const add = (token, from, to, start) => {
    if (to !== from) found.push({ token, from, to, start, length: from.length });
  };
  tokens.forEach((token, index) => {
    const next = tokens[index + 1];
    const previous = tokens[index - 1];
    if (profile === 'config') {
      if (token.type !== 'ident') return;
      const isKey = next?.type === 'op' && next.value === ':';
      const isPlaceholderName = previous?.value === '{' && tokens[index - 2]?.value === '$';
      if (isKey) add(token, token.value, mapComposite(token.value), token.offset);
      else if (isPlaceholderName) add(token, token.value, UPPER.test(token.value) ? (map.ident.get(token.value) ?? token.value) : mapComposite(token.value), token.offset);
      else if (Object.hasOwn(configValues, token.value) && !keep.has(token.value)) add(token, token.value, configValues[token.value], token.offset);
    } else if (profile === 'env') {
      if (token.type === 'ident' || token.type === 'string') {
        const body = token.type === 'string' ? unquote(token.value) : token.value;
        add(token, body, token.type === 'string' ? mapEnvWords(body) : envNames.has(body) ? (map.ident.get(body) ?? body) : body, token.offset + (token.type === 'string' ? 1 : 0));
      }
    } else if (token.type === 'ident' || token.type === 'key') {
      const body = token.type === 'key' ? unquote(token.value) : token.value;
      add(token, body, mapComposite(body), token.offset + (token.type === 'key' ? 1 : 0));
    } else if (schemaKeys && token.type === 'string') {
      // esquema JSON: valor que cita propriedade (`required`) ou `#/$defs/nome`
      const body = unquote(token.value);
      const cut = body.startsWith('#/') ? body.lastIndexOf('/') + 1 : 0;
      const name = body.slice(cut);
      if (schemaKeys.has(name)) add(token, body, body.slice(0, cut) + mapComposite(name), token.offset + 1);
    } else if (token.type === 'string' && (strings || kind === 'html' || kind === 'xml')) {
      const body = unquote(token.value);
      let to = body;
      const attribute = tokens[index - 2]?.value;
      // th:each e th:with declaram variáveis do modelo: o nome declarado acompanha os usos dentro de ${…}
      if (tokens[index - 3]?.value === ':' && (attribute === 'each' || attribute === 'with')) to = mapEmbedded(body, map);
      else if (/\$\{/.test(body)) to = mapTemplate(body);
      else if ((kind === 'html' || kind === 'xml') && previous?.value === '=' && tokens[index - 2]?.value === 'class') to = mapClasses(body);
      else if (strings && (isSingleWord(body) || isIdentifierWord(body))) to = mapComposite(body);
      else if (strings && looksLikeCode(body)) to = mapEmbedded(body, map);
      add(token, body, to, token.offset + 1);
    }
  });
  return found;
}

let total = 0;
for (const arg of files) {
  const file = resolve(root, arg);
  const path = relative(root, file).split(sep).join('/');
  const kind = kindOf(path);
  const source = readFileSync(file, 'utf8');
  const { tokens } = lexSimple(kind, source);
  const replacements = replacementsFor(kind, tokens);
  if (!replacements.length) continue;
  total += replacements.length;
  for (const replacement of replacements) console.log(`${path}:${replacement.token.line}  ${JSON.stringify(replacement.from).slice(0, 100)}  →  ${JSON.stringify(replacement.to).slice(0, 100)}`);
  if (apply) {
    let text = source;
    for (const replacement of [...replacements].sort((a, b) => b.start - a.start)) text = text.slice(0, replacement.start) + replacement.to + text.slice(replacement.start + replacement.length);
    writeFileSync(file, text);
  }
}
console.log(`${apply ? 'aplicadas' : 'candidatas'}: ${total} trocas em ${files.length} arquivo(s)`);
