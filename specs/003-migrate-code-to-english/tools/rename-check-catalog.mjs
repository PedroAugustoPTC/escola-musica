// Modo `--catalog` do rename-check (R-04, T054): prova de que o catálogo de mensagens só mudou de chave e de marcador.
//
//   node rename-check.mjs --catalog <catálogo-base.properties> <catálogo-novo.properties>
//        [--keys <mapa-de-chaves.json>] [--markers <mapa-de-marcadores.json>]
//        [--json-base <mensagens.json base> --json-novo <mensagens.json novo>]
//
// Os mapas são objetos `{ "antiga": "nova" }`. Sem `--keys`, as chaves ficam iguais (fatia 3: só o arquivo muda de lugar).
// Sai com código ≠ 0 se faltar chave, sobrar chave, o texto mudar ou surgir marcador fora do mapa.
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { compareCatalogs, compareJsonMessages, parseCatalog } from './lib/catalog.mjs';

const args = process.argv.slice(2);
const option = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : undefined);
const flags = new Set(['--keys', '--markers', '--json-base', '--json-novo']);
const positional = args.filter((arg, index) => arg !== '--catalog' && !flags.has(arg) && !flags.has(args[index - 1]));
if (positional.length !== 2) {
  console.error('Uso: rename-check.mjs --catalog <base.properties> <novo.properties> [--keys mapa.json] [--markers mapa.json] [--json-base a.json --json-novo b.json]');
  process.exit(2);
}
const load = (path) => JSON.parse(readFileSync(resolve(path), 'utf8'));
const options = { keyMap: option('--keys') ? load(option('--keys')) : {}, markerMap: option('--markers') ? load(option('--markers')) : {} };
const oldCatalog = parseCatalog(readFileSync(resolve(positional[0]), 'utf8'));
const newCatalog = parseCatalog(readFileSync(resolve(positional[1]), 'utf8'));
const errors = compareCatalogs(oldCatalog, newCatalog, options);
console.log(`catálogo: ${oldCatalog.keys.length} chaves na base, ${newCatalog.keys.length} no novo, ${Object.keys(options.keyMap).length} chaves e ${Object.keys(options.markerMap).length} marcadores pelo mapa`);
if (option('--json-base') && option('--json-novo')) {
  const jsonErrors = compareJsonMessages(load(option('--json-base')), load(option('--json-novo')), options);
  console.log(`mensagens.json: ${jsonErrors.length} divergências`);
  errors.push(...jsonErrors.map((error) => ({ ...error, detail: `(mensagens.json) ${error.detail}` })));
}
console.log(`${errors.length} divergências`);
for (const error of errors.slice(0, 60)) console.log(`${error.kind}: ${error.key} ${error.detail}`);
process.exit(errors.length ? 1 : 0);
