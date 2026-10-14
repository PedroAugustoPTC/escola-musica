// Modo `--contracts` do rename-check (T108, T114): contrato antigo × novo com a mesma forma, só nomes diferentes.
//
//   node rename-check.mjs --contracts [--old-root <árvore-base>] [--new-root <raiz>] [--out tools/api-field-map.json]
//
// Os pares: Portal (OpenAPI), ações do canal (JSON Schema) e canal interno (OpenAPI). A árvore antiga é a base da fatia
// (`git archive` do commit anterior); a nova é o repositório (contratos no lugar final ou ainda em specs/003/contracts).
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { compareContracts, flattenNames, loadContract } from './lib/contracts.mjs';

const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const newRoot = resolve(option('--new-root', process.cwd()));
const oldRoot = resolve(option('--old-root', newRoot));
const out = option('--out');
const staged = 'specs/003-migrate-code-to-english/contracts';
const PAIRS = [
  ['api-portal', 'specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml', ['specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml', `${staged}/api-portal.openapi.yaml`]],
  ['actions', 'specs/002-api-interna-canal-telegram/contracts/acoes-v1.schema.json', ['specs/002-api-interna-canal-telegram/contracts/actions-v1.schema.json', `${staged}/actions-v1.schema.json`]],
  ['telegram-channel-internal', 'specs/002-api-interna-canal-telegram/contracts/canal-telegram-interno.openapi.yaml', ['specs/002-api-interna-canal-telegram/contracts/telegram-channel-internal.openapi.yaml', `${staged}/telegram-channel-internal.openapi.yaml`]],
];
let failed = 0;
const merged = {};
const allConflicts = [];
for (const [id, oldRel, newCandidates] of PAIRS) {
  // o contrato novo é sempre a cópia de specs/003 (a do lugar final tem de ser byte a byte igual: conferido abaixo)
  const stagedRel = newCandidates.find((candidate) => candidate.startsWith(staged));
  const oldDoc = loadContract(join(oldRoot, oldRel), newRoot);
  const effective = { ...compareContracts(oldDoc, loadContract(join(newRoot, stagedRel), newRoot)), chosen: stagedRel };
  const placed = newCandidates.find((candidate) => !candidate.startsWith(staged) && candidate !== oldRel) ?? (oldRoot !== newRoot ? oldRel : null);
  if (placed && existsSync(join(newRoot, placed)) && existsSync(join(newRoot, stagedRel))) {
    const same = readFileSync(join(newRoot, placed)).equals(readFileSync(join(newRoot, stagedRel)));
    console.log(`  ${placed}: ${same ? 'idêntico byte a byte à cópia de specs/003' : 'DIFERENTE da cópia de specs/003'}`);
    if (!same) failed++;
  }
  const { flat, conflicts } = flattenNames(effective.names);
  const count = Object.values(flat).reduce((sum, kind) => sum + Object.keys(kind).length, 0);
  console.log(`${id}: ${effective.errors.length} diferenças de forma, ${count} nomes trocados (${oldRel} × ${effective.chosen})`);
  for (const error of effective.errors.slice(0, 40)) console.log(`  FORMA ${error}`);
  failed += effective.errors.length;
  for (const conflict of conflicts) allConflicts.push(`${id}: ${conflict}`);
  merged[id] = flat;
}
console.log(`${allConflicts.length} nomes com mais de um destino (informativo; revisar se o antigo é o mesmo conceito)`);
for (const conflict of allConflicts) console.log(`  ${conflict}`);
if (out) {
  writeFileSync(resolve(newRoot, out), `${JSON.stringify(merged, null, 2)}\n`);
  console.log(`mapa gravado em ${out}`);
}
process.exitCode = failed ? 1 : 0;
