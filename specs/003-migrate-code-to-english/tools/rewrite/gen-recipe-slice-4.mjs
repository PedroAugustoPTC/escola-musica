#!/usr/bin/env node
// Gera a receita de renomeação da fatia 4 (T105, T116, T117): os nomes que cruzam o fio e a retenção B1/B2 da fatia 3.
//
//   node gen-recipe-slice-4.mjs [--root <raiz>]   →   slice-4.yml, slice-4-members.json
//
// Estado de partida: o commit da fatia 3. Nenhum pacote muda; o que sobra em português nos dois módulos são as constantes
// de enumeração (B1), os componentes de DTO de fio e os parâmetros de rota com nome implícito (B2). A receita própria
// (RenameMembersByMap) renomeia, em `core` e `application`, todo símbolo DECLARADO cujo nome está no mapa da fatia 4 — o
// mapa só tem o que ainda é português, gerado do glossário e do contrato novo (`maps/slice-4.review.01.json`).
import { writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { loadRenameMap } from '../lib/rename-map.mjs';

const args = process.argv.slice(2);
const root = resolve(args.includes('--root') ? args[args.indexOf('--root') + 1] : process.cwd());
const TOOLS = 'specs/003-migrate-code-to-english/tools';
const map = loadRenameMap(root, 4);
const identifier = /^[A-Za-z_$][A-Za-z0-9_$]*$/;
// nomes antigos do banco e de arquivos de dados: nunca são símbolos Java
const noise = /_not_null$|_fkey$|_pkey$|_seq_seq$/;
const names = {};
for (const [antigo, novo] of map.ident) {
  if (!identifier.test(antigo) || !identifier.test(novo) || noise.test(antigo)) continue;
  names[antigo] = novo;
}
const extra = map.explicit.slice4 ?? {};
const members = {
  gerado_por: 'tools/rewrite/gen-recipe-slice-4.mjs (T105)',
  corePackages: ['br.com.felipeblaft.reports'],
  exactTypes: [],
  names,
  methodNames: extra.methodIdentifiers ?? {},
  qualifiedMethods: extra.qualifiedMethods ?? {},
  qualifiedFields: extra.qualifiedFields ?? {},
  variableNames: extra.variableIdentifiers ?? {},
  held: {},
};
writeFileSync(join(root, TOOLS, 'rewrite/slice-4-members.json'), `${JSON.stringify(members, null, 2)}\n`);
const yml = [
  '---',
  'type: specs.openrewrite.org/v1beta/recipe',
  'name: br.com.felipeblaft.migration.Slice4.Members',
  'displayName: "Fatia 4 — membros do fio e B1"',
  'description: "Constantes de enumeração, componentes de DTO e parâmetros de rota pelo mapa da fatia 4."',
  'recipeList:',
  '  - br.com.felipeblaft.migration.rewrite.RenameMembersByMap',
  '---',
  'type: specs.openrewrite.org/v1beta/recipe',
  'name: br.com.felipeblaft.migration.Slice4.All',
  'displayName: "Fatia 4 — tudo"',
  'description: "Só membros: nenhum pacote muda na fatia 4."',
  'recipeList:',
  '  - br.com.felipeblaft.migration.Slice4.Members',
];
writeFileSync(join(root, TOOLS, 'rewrite/slice-4.yml'), `${yml.join('\n')}\n`);
console.log(`nomes: ${Object.keys(names).length}`);
