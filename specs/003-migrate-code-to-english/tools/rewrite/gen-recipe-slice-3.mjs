#!/usr/bin/env node
// Gera a receita de renomeação da fatia 3 (módulo `application`) a partir do mapa revisado e da retenção (T059).
//
//   node gen-recipe-slice-3.mjs [--root <raiz>]
//
// Saídas em tools/rewrite/:
//   slice-3.yml            Packages (ChangePackage), Members (receita Java própria: tipos e membros) e All (Members, Packages);
//   slice-3-members.json   configuração da receita Java própria (mapa, dono, retenção);
//   slice-3-packages.json  tabela de pacotes antigo → novo (lida pelo rename-check).
//
// Estado de partida: o `core` já migrou (fatia 2) e o módulo foi movido para `backend/application` (T060); os pacotes e
// tipos da aplicação ainda têm os nomes antigos. A ordem importa como na fatia 2: Members (tipos e membros, com os tipos
// íntegros) antes de Packages (ChangePackage tira supertipos e métodos dos tipos analisados).
//   - dono dos símbolos: tipos dos pacotes da aplicação (nomes antigos e novos) e, por tipo exato, a classe raiz;
//   - a retenção (`held-back/slice-3.json`) usa o caminho ANTIGO do tipo (sem pacote), como na fatia 2.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { lex } from '../lib/java-lexer.mjs';
import { loadRenameMap } from '../lib/rename-map.mjs';

const args = process.argv.slice(2);
const root = resolve(args.includes('--root') ? args[args.indexOf('--root') + 1] : process.cwd());
// a receita parte do estado ANTIGO da aplicação (módulo e pacotes com os nomes da fatia 2): `--source-root` aponta para a
// árvore-base e `--module-dir` para a pasta do módulo nela (`backend/aplicacao`); as saídas vão sempre para `--root`
const SOURCE_ROOT = resolve(args.includes('--source-root') ? args[args.indexOf('--source-root') + 1] : root);
const MODULE_DIR = args.includes('--module-dir') ? args[args.indexOf('--module-dir') + 1] : 'backend/application';
const BASE = 'br.com.felipeblaft.reports';
const TOOLS = 'specs/003-migrate-code-to-english/tools';
const map = loadRenameMap(root, 3);

function* javaFiles(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.name === 'target') continue;
    const full = join(directory, entry.name);
    if (entry.isDirectory()) yield* javaFiles(full);
    else if (entry.name.endsWith('.java')) yield full;
  }
}

function packageOf(tokens) {
  const start = tokens.findIndex((token) => token.type === 'ident' && token.value === 'package');
  if (start < 0) return '';
  const parts = [];
  for (let i = start + 1; i < tokens.length && !(tokens[i].type === 'op' && tokens[i].value === ';'); i++) {
    if (tokens[i].type === 'ident') parts.push(tokens[i].value);
  }
  return parts.join('.');
}

const segmentOf = (segment) => map.explicit.packageSegments?.[segment] ?? map.mapIdent(segment);
const packages = new Map(); // pacote antigo → novo
const rootTypes = new Set(); // tipos do pacote-base (nome antigo, qualificado)
for (const file of javaFiles(join(SOURCE_ROOT, MODULE_DIR, 'src'))) {
  const { tokens } = lex(readFileSync(file, 'utf8'));
  const pack = packageOf(tokens);
  if (!pack.startsWith(BASE)) continue;
  const rest = pack.slice(BASE.length).split('.').filter(Boolean);
  packages.set(pack, [BASE, ...rest.map(segmentOf)].join('.'));
  if (rest.length === 0) {
    const name = file.split(/[\\/]/).at(-1).replace(/\.java$/, '');
    rootTypes.add(`${BASE}.${name}`);
  }
}

const changed = [...packages].filter(([antigo, novo]) => antigo !== novo).sort(([a], [b]) => a.localeCompare(b));
const quote = (text) => JSON.stringify(text);
const lines = [];
const header = (name, display, description) => {
  lines.push('---', 'type: specs.openrewrite.org/v1beta/recipe', `name: ${name}`, `displayName: ${quote(display)}`, `description: ${quote(description)}`, 'recipeList:');
};
header('br.com.felipeblaft.migration.Slice3.Packages', 'Fatia 3 — pacotes', 'Pacotes da aplicação pelo mapa (entrada→inbound, saida→outbound, …).');
for (const [antigo, novo] of changed) {
  lines.push('  - org.openrewrite.java.ChangePackage:', `      oldPackageName: ${antigo}`, `      newPackageName: ${novo}`, '      recursive: false');
}
header('br.com.felipeblaft.migration.Slice3.Members', 'Fatia 3 — membros', 'Tipos, métodos, campos, parâmetros, variáveis e componentes de record da aplicação pelo mapa, menos a retenção.');
lines.push('  - br.com.felipeblaft.migration.rewrite.RenameMembersByMap');
header('br.com.felipeblaft.migration.Slice3.All', 'Fatia 3 — tudo', 'Membros (receita Java própria, com os tipos íntegros) e depois pacotes (ChangePackage).');
for (const part of ['Members', 'Packages']) lines.push(`  - br.com.felipeblaft.migration.Slice3.${part}`);
writeFileSync(join(root, TOOLS, 'rewrite/slice-3.yml'), `${lines.join('\n')}\n`);

const held = {};
for (const group of [map.held.B1, map.held.B2]) for (const [path, entry] of Object.entries(group)) held[path] = entry.members;
const names = {};
for (const [antigo, novo] of map.ident) names[antigo] = novo;
const members = {
  gerado_por: 'tools/rewrite/gen-recipe-slice-3.mjs (T059)',
  // o pacote-base inteiro não entra (pegaria o core); os tipos da raiz entram por nome exato
  corePackages: [...new Set(changed.flatMap(([antigo, novo]) => [antigo, novo]).concat([...packages.keys()].filter((pack) => pack !== BASE)))].sort(),
  exactTypes: [...rootTypes].sort(),
  names,
  methodNames: map.explicit.methodIdentifiers ?? {},
  qualifiedMethods: map.explicit.qualifiedMethods ?? {},
  qualifiedFields: map.explicit.qualifiedFields ?? {},
  variableNames: map.explicit.variableIdentifiers ?? {},
  held,
};
writeFileSync(join(root, TOOLS, 'rewrite/slice-3-packages.json'), `${JSON.stringify(Object.fromEntries(changed), null, 2)}\n`);
writeFileSync(join(root, TOOLS, 'rewrite/slice-3-members.json'), `${JSON.stringify(members, null, 2)}\n`);
console.log(`pacotes: ${packages.size} (${changed.length} mudam); tipos da raiz: ${rootTypes.size}; nomes: ${map.ident.size}; retidos: ${Object.keys(held).length} tipos`);

// ---- B2 (T075): componentes de record retidos na fatia 2 (esquemas de IA, jsonb, arquivo de carga) -----------------------
// Receita de membros própria: só os nomes dos componentes listados em `held-back/slice-2.json` (B2), nos dois módulos.
// Roda DEPOIS da Slice3.All; a retenção da fatia 2 sai (os componentes passam a ter o nome do esquema novo).
const heldSlice2 = JSON.parse(readFileSync(join(root, TOOLS, 'held-back/slice-2.json'), 'utf8'));
// dono: SÓ os tipos B2 do core (não o pacote-base inteiro: os DTOs de fio da aplicação têm componentes com os mesmos nomes
// e continuam retidos). O nome qualificado novo vem da tabela de pacotes e do mapa da fatia 2.
const map2 = loadRenameMap(root, 2);
const slice2Packages = JSON.parse(readFileSync(join(root, TOOLS, 'rewrite/slice-2-packages.json'), 'utf8'));
const b2Types = new Set();
for (const [path, entry] of Object.entries(heldSlice2.B2)) {
  const sourceFile = entry.file.split('/src/main/java/')[1] ?? entry.file.split('/src/test/java/')[1];
  const oldPackage = sourceFile.split('/').slice(0, -1).join('.');
  const newPackage = slice2Packages[oldPackage] ?? oldPackage;
  const top = path.split('.')[0];
  b2Types.add(`${newPackage}.${map2.mapIdent(top)}`);
}
const b2Names = {};
for (const entry of Object.values(heldSlice2.B2)) for (const member of entry.members) b2Names[member] = map.mapIdent(member);
const missing = Object.entries(b2Names).filter(([antigo, novo]) => antigo === novo).map(([antigo]) => antigo);
const b2 = {
  gerado_por: 'tools/rewrite/gen-recipe-slice-3.mjs (T075, B2)',
  corePackages: [],
  exactTypes: [...b2Types].sort(),
  names: b2Names,
  methodNames: {},
  qualifiedMethods: {},
  qualifiedFields: {},
  variableNames: {},
  held: {},
};
writeFileSync(join(root, TOOLS, 'rewrite/slice-3-b2-members.json'), `${JSON.stringify(b2, null, 2)}\n`);
const b2Lines = [];
b2Lines.push(
  '---',
  'type: specs.openrewrite.org/v1beta/recipe',
  'name: br.com.felipeblaft.migration.Slice3.B2',
  'displayName: "Fatia 3 — componentes de record retidos (B2)"',
  'description: "Componentes de record lidos pelo Jackson (esquemas de IA, jsonb, arquivo de carga) passam ao nome do esquema novo."',
  'recipeList:',
  '  - br.com.felipeblaft.migration.rewrite.RenameMembersByMap',
);
writeFileSync(join(root, TOOLS, 'rewrite/slice-3-b2.yml'), `${b2Lines.join('\n')}\n`);
console.log(`B2: ${Object.keys(b2Names).length} nomes de componente em ${b2Types.size} tipos${missing.length ? `; sem nome novo no mapa (ficam iguais): ${missing.join(', ')}` : ''}`);
