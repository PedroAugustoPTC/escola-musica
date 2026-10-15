#!/usr/bin/env node
// Gera a receita de renomeação da fatia 2 a partir do mapa revisado e da lista de retenção (T033).
//
//   node gen-recipe.mjs --slice 2 [--root <raiz>]
//
// Saídas em tools/rewrite/:
//   slice-2.yml            Packages (T036, ChangePackage), Members (T037/T038, receita Java própria: tipos e membros) e All;
//   slice-2-members.json   configuração da receita Java própria (mapa, pacotes do core, retenção).
//
// Estado de partida de cada receita (as tasks rodam em ordem): Packages parte da árvore com `backend/core` movido e
// pacotes antigos; Types parte dos pacotes novos; Members parte dos tipos novos. Por isso:
//   - Types usa o pacote NOVO nos nomes qualificados;
//   - Members identifica retidos pelo caminho NOVO do tipo.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import { lex } from '../lib/java-lexer.mjs';
import { scanTypes } from '../lib/java-types.mjs';
import { loadRenameMap } from '../lib/rename-map.mjs';

const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const slice = Number(option('--slice', '0'));
const root = resolve(option('--root', process.cwd()));
if (slice !== 2) {
  console.error('Só a fatia 2 está configurada.');
  process.exit(2);
}
const map = loadRenameMap(root, slice);
const OLD_BASE = 'br.com.felipeblaft.relatorios';
const NEW_BASE = 'br.com.felipeblaft.reports';
// onde está o código de cada módulo ao rodar a receita (depois de T034 o módulo se chama `core`)
// a receita parte do estado ANTIGO: --source-root aponta para a árvore-base (o commit da fatia 1), com `backend/nucleo`
const SOURCE_ROOT = resolve(option('--source-root', root));
const CORE_DIR = option('--core-dir', SOURCE_ROOT === root ? 'backend/core' : 'backend/nucleo');
const APPLICATION_DIR = 'backend/aplicacao';

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

const oldPackages = new Map(); // pacote antigo → { novo, modulo }
const coreTypes = []; // { pacoteNovo, caminhoAntigo }

// aplicacao primeiro: o pacote compartilhado (`arquitetura`) segue o mapeamento do core
for (const [module, directory] of [['application', APPLICATION_DIR], ['core', CORE_DIR]]) {
  for (const file of javaFiles(join(SOURCE_ROOT, directory, 'src'))) {
    const { tokens } = lex(readFileSync(file, 'utf8'));
    const pack = packageOf(tokens);
    if (!pack.startsWith(OLD_BASE)) continue;
    const rest = pack.slice(OLD_BASE.length).split('.').filter(Boolean);
    // segmentos do aplicacao seguem iguais nesta fatia (entrada, saida, configuracao… migram na fatia 3)
    const mapped = module === 'core' ? rest.map((segment) => map.explicit.packageSegments?.[segment] ?? map.mapIdent(segment)) : rest;
    const novo = [NEW_BASE, ...mapped].join('.');
    oldPackages.set(pack, { novo, module });
    if (module === 'core') for (const type of scanTypes(tokens)) coreTypes.push({ pacoteNovo: novo, caminho: type.path.split('.'), file });
  }
}

const quote = (text) => JSON.stringify(text);
const lines = [];
const header = (name, display, description) => {
  lines.push('---', 'type: specs.openrewrite.org/v1beta/recipe', `name: ${name}`, `displayName: ${quote(display)}`, `description: ${quote(description)}`, 'recipeList:');
};

// ---- Packages (T036) ------------------------------------------------------------------------------------------
header('br.com.felipeblaft.migration.Slice2.Packages', 'Fatia 2 — pacotes', 'Base relatorios→reports nos dois módulos; camadas e subpacotes do core pelo mapa.');
for (const [antigo, { novo }] of [...oldPackages].sort(([a], [b]) => a.localeCompare(b))) {
  lines.push('  - org.openrewrite.java.ChangePackage:', `      oldPackageName: ${antigo}`, `      newPackageName: ${novo}`, '      recursive: false');
}

// ---- Members (T038) ---------------------------------------------------------------------------------------------
header('br.com.felipeblaft.migration.Slice2.Members', 'Fatia 2 — membros', 'Métodos, campos, parâmetros, variáveis e componentes de record do core pelo mapa, menos a retenção.');
lines.push('  - br.com.felipeblaft.migration.rewrite.RenameMembersByMap'); // lê -Drename.mapFile (ver run-recipe.sh)

// ---- All: as três em ordem, numa execução só (cada execução reanalisa os dois módulos: ~9 min) -----------------------
header('br.com.felipeblaft.migration.Slice2.All', 'Fatia 2 — tudo', 'Tipos e membros (receita Java própria, com os tipos ainda íntegros) e depois pacotes (ChangePackage).');
for (const part of ['Members', 'Packages']) lines.push(`  - br.com.felipeblaft.migration.Slice2.${part}`);
writeFileSync(join(root, 'specs/003-migrate-code-to-english/tools/rewrite/slice-2.yml'), `${lines.join('\n')}\n`);

const newPathOf = (oldPath) => oldPath.split('.').map((name) => map.mapIdent(name)).join('.');
const held = {};
for (const group of [map.held.B1, map.held.B2]) {
  for (const [path, entry] of Object.entries(group)) {
    // os tipos mantêm o nome antigo durante a execução (a receita renomeia o texto, não o tipo): chave = caminho antigo
    held[path] = entry.members;
  }
}
const names = {};
for (const [antigo, novo] of map.ident) names[antigo] = novo;
const members = {
  gerado_por: 'tools/rewrite/gen-recipe.mjs (T033)',
  // nomes novos e antigos: os tipos analisados guardam, em partes, o pacote antigo durante a execução
  corePackages: [...new Set([...oldPackages].filter(([, p]) => p.module === 'core').flatMap(([antigo, p]) => [antigo, p.novo]))].sort(),
  names,
  methodNames: map.explicit.methodIdentifiers ?? {},
  qualifiedMethods: map.explicit.qualifiedMethods ?? {},
  variableNames: map.explicit.variableIdentifiers ?? {},
  held,
};
writeFileSync(
  join(root, 'specs/003-migrate-code-to-english/tools/rewrite/slice-2-packages.json'),
  `${JSON.stringify(Object.fromEntries([...oldPackages].map(([antigo, { novo }]) => [antigo, novo])), null, 2)}
`,
);
writeFileSync(join(root, 'specs/003-migrate-code-to-english/tools/rewrite/slice-2-members.json'), `${JSON.stringify(members, null, 2)}\n`);
console.log(`pacotes: ${oldPackages.size} (${[...oldPackages.values()].filter((p) => p.module === 'core').length} do core); nomes: ${map.ident.size}; retidos: ${Object.keys(held).length} tipos`);
void relative; void sep;
