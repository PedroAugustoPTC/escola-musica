#!/usr/bin/env node
// Inventário fechado do frontend (task T150): só levanta dados, não altera nada.
//
//   TSMORPH_FROM=<pasta com node_modules/ts-morph> node frontend-inventory.mjs [--root <raiz>] [--out <arquivo>]
//
// Grava `tools/frontend-inventory.json` com: arquivos e pastas de `frontend/{src,tests,public,scripts}`, identificadores de código
// (nomes que aparecem como `Identifier` no TypeScript, nunca dentro de texto), literais de uma palavra por posição (`queryKey`,
// `data-testid`, `id`, `htmlFor`, `name`, rotas…), classes e variáveis do CSS, classes usadas em `className`, rotas da SPA,
// ativos de marca e scripts do `package.json`. É a base do mapa (`gen-identifier-map.mjs --slice 5`) e do conjunto fechado do CSS.
import { createRequire } from 'node:module';
import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';

const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const root = resolve(option('--root', process.cwd()));
const out = resolve(root, option('--out', 'specs/003-migrate-code-to-english/tools/frontend-inventory.json'));
const requireFrom = createRequire(join(process.env.TSMORPH_FROM ?? root, 'package.json'));
const { Project, SyntaxKind, Node } = requireFrom('ts-morph');

const frontend = join(root, 'frontend');
const GENERATED = /(api-tipos|api-types)\.ts$|mensagens\.json$|messages\.json$/;
const rel = (file) => relative(frontend, file).split(sep).join('/');

const identifiers = new Map(); // nome → { declarado, arquivos[] }
const note = (name, file, declared) => {
  if (!identifiers.has(name)) identifiers.set(name, { declared: false, files: [] });
  const entry = identifiers.get(name);
  entry.declared ||= declared;
  if (entry.files.length < 3 && !entry.files.includes(file)) entry.files.push(file);
};

const literals = new Map(); // valor → Set(posição)
const noteLiteral = (value, position, file) => {
  if (!literals.has(value)) literals.set(value, { positions: new Set(), files: new Set() });
  literals.get(value).positions.add(position);
  if (literals.get(value).files.size < 3) literals.get(value).files.add(file);
};

const classNames = new Map(); // classe usada em className → arquivos
const noteClass = (value, file) => {
  if (!classNames.has(value)) classNames.set(value, new Set());
  if (classNames.get(value).size < 3) classNames.get(value).add(file);
};
const routes = new Map();
const noteRoute = (value, file) => {
  if (!routes.has(value)) routes.set(value, new Set());
  routes.get(value).add(file);
};

const project = new Project({ tsConfigFilePath: join(frontend, 'tsconfig.json') });
const oneWord = /^[A-Za-z][A-Za-z0-9_-]*$/;

function declarationName(node) {
  const parent = node.getParent();
  return Boolean(parent && typeof parent.getNameNode === 'function' && parent.getNameNode() === node);
}

function positionOf(literal) {
  // sobe por `cond ? 'a' : 'b'`, `(x)`, `{x}`, `x && 'a'` até o que de fato recebe o literal
  let child = literal;
  let parent = literal.getParent();
  while (
    parent &&
    ((Node.isConditionalExpression(parent) && parent.getCondition() !== child) ||
      Node.isParenthesizedExpression(parent) ||
      Node.isJsxExpression(parent) ||
      Node.isAsExpression(parent) ||
      (Node.isBinaryExpression(parent) && ['&&', '||', '??'].includes(parent.getOperatorToken().getText())))
  ) {
    child = parent;
    parent = parent.getParent();
  }
  if (Node.isJsxAttribute(parent)) return `jsx:${parent.getNameNode().getText()}`;
  if (Node.isPropertyAssignment(parent)) return `property:${parent.getName()}`;
  if (Node.isArrayLiteralExpression(parent)) {
    const holder = parent.getParent();
    if (Node.isPropertyAssignment(holder)) return `array:${holder.getName()}`;
    return 'array';
  }
  if (Node.isCallExpression(parent)) return `call:${parent.getExpression().getText().slice(0, 40)}`;
  if (Node.isBinaryExpression(parent)) return 'comparison';
  if (Node.isLiteralTypeNode(parent)) return 'type';
  if (Node.isCaseClause(parent)) return 'case';
  return parent ? parent.getKindName() : 'other';
}

for (const source of project.getSourceFiles()) {
  const file = rel(source.getFilePath());
  if (file.startsWith('..') || file.includes('node_modules') || GENERATED.test(file)) continue;
  for (const node of source.getDescendantsOfKind(SyntaxKind.Identifier)) note(node.getText(), file, declarationName(node));
  for (const literal of [...source.getDescendantsOfKind(SyntaxKind.StringLiteral), ...source.getDescendantsOfKind(SyntaxKind.NoSubstitutionTemplateLiteral)]) {
    const value = literal.getLiteralText();
    const position = positionOf(literal);
    if (position === 'jsx:className') for (const name of value.split(/\s+/).filter(Boolean)) noteClass(name, file);
    if (value.startsWith('/') && /^\/[A-Za-z0-9:/_?=&.-]*$/.test(value)) noteRoute(value, file);
    if (oneWord.test(value) && position !== 'jsx:className') noteLiteral(value, position, file);
  }
  for (const template of source.getDescendantsOfKind(SyntaxKind.TemplateExpression)) {
    const head = template.getHead().getLiteralText();
    const position = positionOf(template);
    if (position === 'jsx:className' || (Node.isJsxExpression(template.getParent()) && template.getParent().getParent()?.getNameNode?.().getText() === 'className')) {
      for (const name of head.split(/\s+/).filter(Boolean)) noteClass(name, file);
      for (const span of template.getTemplateSpans()) for (const name of span.getLiteral().getLiteralText().split(/\s+/).filter(Boolean)) noteClass(name, file);
    }
    if (head.startsWith('/')) noteRoute(template.getText().slice(1, -1), file);
  }
}

// CSS
const cssClasses = new Map();
const cssVariables = new Map();
const cssUrls = [];
const walk = (directory) =>
  existsSync(directory)
    ? readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
        const full = join(directory, entry.name);
        return entry.isDirectory() ? walk(full) : [full];
      })
    : [];
const allFiles = ['src', 'tests', 'public', 'scripts'].flatMap((folder) => walk(join(frontend, folder))).map(rel).sort();
for (const file of allFiles.filter((name) => name.endsWith('.css'))) {
  const stripped = readFileSync(join(frontend, file), 'utf8').replace(/\/\*[\s\S]*?\*\//g, ' ');
  // seletores: só o que está antes de `{` (as declarações não têm `.classe`)
  for (const block of stripped.matchAll(/([^{}]+)\{/g)) {
    if (/^\s*@(font-face|media|keyframes|supports)/.test(block[1])) continue;
    for (const found of block[1].matchAll(/\.([A-Za-z_][A-Za-z0-9_-]*)/g)) {
      if (!cssClasses.has(found[1])) cssClasses.set(found[1], new Set());
      cssClasses.get(found[1]).add(file);
    }
  }
  // propriedade personalizada: `--nome:` (declaração) ou `var(--nome` (uso); `--modificador` de classe BEM não é variável
  for (const found of stripped.matchAll(/(?:^|[\s{;])--([A-Za-z][A-Za-z0-9-]*)\s*:|var\(\s*--([A-Za-z][A-Za-z0-9-]*)/g)) {
    found[1] ??= found[2];
    if (!cssVariables.has(found[1])) cssVariables.set(found[1], new Set());
    cssVariables.get(found[1]).add(file);
  }
  for (const found of stripped.matchAll(/url\(\s*['"]?([^)'"]+)['"]?\s*\)/g)) cssUrls.push({ file, url: found[1] });
}

const packageJson = JSON.parse(readFileSync(join(frontend, 'package.json'), 'utf8'));
const sorted = (map, fields) => [...map].sort(([a], [b]) => a.localeCompare(b)).map(([name, value]) => ({ name, ...fields(value) }));
const folders = [...new Set(allFiles.flatMap((file) => file.split('/').slice(0, -1).map((_, index, parts) => parts.slice(0, index + 1).join('/'))))].sort();

const inventory = {
  comment: 'Gerado por tools/frontend-inventory.mjs (T150). Só dados; não editar à mão.',
  folders,
  files: allFiles,
  scripts: packageJson.scripts,
  brandAssets: allFiles.filter((file) => file.startsWith('public/')),
  identifiers: sorted(identifiers, (value) => value),
  literals: sorted(literals, (value) => ({ positions: [...value.positions].sort(), files: [...value.files] })),
  classNamesUsed: sorted(classNames, (value) => ({ files: [...value] })),
  cssClasses: sorted(cssClasses, (value) => ({ files: [...value] })),
  cssVariables: sorted(cssVariables, (value) => ({ files: [...value] })),
  cssUrls,
  routes: sorted(routes, (value) => ({ files: [...value] })),
};
writeFileSync(out, `${JSON.stringify(inventory, null, 2)}\n`);
console.log(
  `inventário: ${allFiles.length} arquivos, ${identifiers.size} identificadores, ${literals.size} literais de uma palavra, ` +
    `${cssClasses.size} classes CSS (${classNames.size} usadas em className), ${cssVariables.size} variáveis CSS, ${routes.size} rotas`,
);
