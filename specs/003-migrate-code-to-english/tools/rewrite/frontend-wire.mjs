#!/usr/bin/env node
// Fatia 4 (T134 a T137): troca no frontend os nomes que tocam o fio, antes de regenerar `api-tipos.ts`.
// Roda no contêiner `node:24-alpine` com `ts-morph` instalado fora do package.json (`npm install --no-save ts-morph`).
//
//   node specs/003-migrate-code-to-english/tools/rewrite/frontend-wire.mjs [--apply]
//
// 1. propriedades dos esquemas gerados (`components['schemas']`): renomeadas como SÍMBOLO (o compilador acha todos os
//    usos: acessos, desestruturação, literais de objeto com tipo, atributos de JSX), pelo mapa do contrato;
// 2. chaves de esquema em `Esquemas['X']`/`components['schemas']['X']`;
// 3. literais de texto que são nome de fio: valores de enumeração, códigos de erro e chaves do catálogo (CAIXA_ALTA que
//    está no mapa), caminhos de API (serviços e mocks de teste) e nomes de parâmetro de consulta.
// Textos para pessoas (JSX, mensagens) nunca são tocados. O que sobrar aparece no `tsc`/`vitest` e nas tarefas seguintes.
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join, relative, sep } from 'node:path';

const root = process.env.REPO_ROOT ?? '/work';
const frontend = join(root, 'frontend');
const require = createRequire(join(frontend, 'package.json'));
const { Project, SyntaxKind, Node } = require('ts-morph');

const apply = process.argv.includes('--apply');
const tools = join(root, 'specs/003-migrate-code-to-english/tools');
const api = JSON.parse(readFileSync(join(tools, 'api-field-map.json'), 'utf8'))['api-portal'];
const explicit = JSON.parse(readFileSync(join(tools, 'explicit-pairs.json'), 'utf8'));
const ident = new Map(JSON.parse(readFileSync(join(tools, 'maps/slice-4.json'), 'utf8')).map((item) => [item.antigo, item.novo]));

const propertyMap = { ...api.property };
const schemaMap = api.schema;
const enumMap = api.enum;
const segmentMap = { ...api['path-segment'], inicio: 'home' };
const parameterMap = { ...api.parameter, ...api['path-parameter'] };

/** O literal de tipo é chave de `Pick<T, 'a' | 'b'>`/`Omit<…>` (e não um valor de união local da interface). */
function isKeyOfPick(literalType) {
  let node = literalType.getParent();
  while (node && Node.isUnionTypeNode(node)) node = node.getParent();
  return Boolean(node && Node.isTypeReference(node) && ['Pick', 'Omit'].includes(node.getTypeName().getText()));
}

/** O nó é o primeiro argumento de `api.get/post/delete(...)` ou `requisitar(...)` (chamada de API fora da pasta de serviços). */
/** O literal é argumento de `x.url.endsWith/includes/startsWith(...)`: fragmento do caminho da API numa asserção de teste. */
function isUrlFragmentTest(literal) {
  const call = literal.getParent();
  if (!call || !Node.isCallExpression(call) || call.getArguments()[0] !== literal) return false;
  const callee = call.getExpression();
  return Node.isPropertyAccessExpression(callee) && ['endsWith', 'includes', 'startsWith'].includes(callee.getName()) && /url/i.test(callee.getExpression().getText());
}

function isApiArgument(node) {
  const call = node.getParent();
  return Boolean(call && Node.isCallExpression(call) && call.getArguments()[0] === node && /^(api\.(get|post|delete)|requisitar)(<.*>)?$/.test(call.getExpression().getText()));
}

const project = new Project({ tsConfigFilePath: join(frontend, 'tsconfig.json') });
const report = { propriedades: 0, esquemas: 0, literais: 0, caminhos: 0 };

// ---- 1. propriedades dos esquemas gerados ------------------------------------------------------------------------
const generated = project.getSourceFileOrThrow(join(frontend, 'src/servicos/api-tipos.ts'));
const components = generated.getInterfaceOrThrow('components');
const schemas = components.getPropertyOrThrow('schemas').getTypeNode();
const properties = schemas.getDescendantsOfKind(SyntaxKind.PropertySignature).filter((property) => {
  // só as propriedades DENTRO de um esquema (não o nome do esquema, que é filho direto de `schemas`)
  return property.getParent()?.getParent() !== schemas.getParent() || false;
});
const seen = new Set();
for (const property of schemas.getDescendantsOfKind(SyntaxKind.PropertySignature)) {
  const nameNode = property.getNameNode();
  const name = property.getName().replace(/^["']|["']$/g, '');
  const parentType = property.getParent();
  // filho direto de `schemas` = nome de esquema (renomeado só em texto, abaixo)
  if (parentType === schemas) continue;
  const novo = propertyMap[name];
  if (!novo || novo === name) continue;
  const key = `${property.getStart()}`;
  if (seen.has(key)) continue;
  seen.add(key);
  if (apply) nameNode.rename(novo);
  report.propriedades++;
}
void properties;

// ---- 2 e 3. literais ---------------------------------------------------------------------------------------------------
const SCREAMING = /^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*$/;
const mapToken = (token, table) => (Object.hasOwn(table, token) ? table[token] : ident.get(token) ?? token);
function mapPath(text) {
  // /alunos/{...} · /alunos?busca= · /relatorios-aula/ · também pedaços de template (`/relatorio-em-aberto`)
  return text.replace(/[A-Za-z][A-Za-z0-9-]*/g, (word, offset, whole) => {
    const before = whole[offset - 1];
    const after = whole[offset + word.length];
    if (before === '=' || before === '&' || before === '?') return word; // valor de consulta
    if (after === '=' && (before === '?' || before === '&')) return mapToken(word, parameterMap);
    return Object.hasOwn(segmentMap, word) ? segmentMap[word] : word;
  });
}
function mapQuery(text) {
  return text.replace(/([?&])([A-Za-z][A-Za-z0-9]*)=/g, (all, sign, name) => `${sign}${Object.hasOwn(parameterMap, name) ? parameterMap[name] : name}=`);
}

const files = project.getSourceFiles().filter((file) => !file.getFilePath().endsWith('api-tipos.ts') && !file.getFilePath().includes('node_modules') && !/language-guard/.test(file.getFilePath()));
for (const file of files) {
  const path = relative(frontend, file.getFilePath()).split(sep).join('/');
  const isService = path.startsWith('src/servicos/');
  const isTest = path.startsWith('tests/');
  const literals = [...file.getDescendantsOfKind(SyntaxKind.StringLiteral), ...file.getDescendantsOfKind(SyntaxKind.NoSubstitutionTemplateLiteral)];
  for (const literal of literals) {
    const value = literal.getLiteralText();
    let novo = value;
    const parent = literal.getParent();
    // Esquemas['X'] / components['schemas']['X']
    const indexed = Node.isLiteralTypeNode(parent) ? parent.getParent() : null;
    if (indexed && Node.isIndexedAccessTypeNode(indexed)) {
      // tipo indexado: `Esquemas['X']` e `components['schemas']['X']` (posição de tipo, não de expressão)
      const target = indexed.getObjectTypeNode().getText();
      if (/Esquemas$|\['schemas'\]$/.test(target) && Object.hasOwn(schemaMap, value)) {
        novo = schemaMap[value];
        report.esquemas++;
      } else if (!SCREAMING.test(value) && Object.hasOwn(propertyMap, value)) {
        novo = propertyMap[value]; // `RelatorioSemestralDto['informacoesGerais']`
      }
    } else if (Node.isLiteralTypeNode(parent) && !SCREAMING.test(value) && Object.hasOwn(propertyMap, value) && isKeyOfPick(parent)) {
      // chave de propriedade em posição de tipo: Pick<T, 'tipo'>, T['informacoesGerais']
      novo = propertyMap[value];
    } else if (Node.isBinaryExpression(parent) && parent.getOperatorToken().getKind() === SyntaxKind.InKeyword && parent.getLeft() === literal && Object.hasOwn(propertyMap, value)) {
      // `'alunoNome' in dados`
      novo = propertyMap[value];
    } else if (SCREAMING.test(value) && ident.has(value) && !/^(GET|POST|DELETE|PUT)$/.test(value)) {
      novo = ident.get(value);
    } else if (!/\s/.test(value) && /[A-Z][A-Z0-9_]{3,}/.test(value) && !value.includes('/api/') && value.replace(/\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*\b/g, (w) => (ident.has(w) ? ident.get(w) : w)) !== value) {
      novo = value.replace(/\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*\b/g, (w) => (ident.has(w) ? ident.get(w) : w));
    } else if (isTest && isUrlFragmentTest(literal)) {
      novo = /^[A-Za-z][A-Za-z0-9]*=/.test(value) ? value.replace(/^[A-Za-z][A-Za-z0-9]*/, (name) => (Object.hasOwn(parameterMap, name) ? parameterMap[name] : name)) : mapQuery(mapPath(value));
    } else if ((value.startsWith('/') && (isService || isApiArgument(literal))) || (isTest && value.includes('/api/v1'))) {
      novo = mapQuery(mapPath(value));
      if (novo !== value) report.caminhos++;
    }
    if (novo !== value) {
      if (process.argv.includes('--list')) console.log(`${path}:${literal.getStartLineNumber()}  ${JSON.stringify(value)} -> ${JSON.stringify(novo)}`);
      report.literais++;
      if (apply) literal.setLiteralValue(novo);
    }
  }
  for (const assignment of file.getDescendantsOfKind(SyntaxKind.PropertyAssignment)) {
    const nameNode = assignment.getNameNode();
    if (Node.isIdentifier(nameNode) && SCREAMING.test(nameNode.getText()) && ident.has(nameNode.getText())) {
      report.literais++;
      if (apply) nameNode.replaceWithText(ident.get(nameNode.getText()));
    }
  }
  // fixtures dos testes: chaves de objeto com o nome de propriedade da API (`{ professor: {...}, sessaoExpiraEm }`)
  if (isTest && path !== 'tests/e2e/suporte.ts' && /simularApi|renderizarRotas/.test(file.getFullText())) {
    for (const assignment of file.getDescendantsOfKind(SyntaxKind.PropertyAssignment)) {
      const nameNode = assignment.getNameNode();
      const text = Node.isStringLiteral(nameNode) ? nameNode.getLiteralText() : nameNode.getText();
      if (!SCREAMING.test(text) && Object.hasOwn(propertyMap, text)) {
        report.literais++;
        if (apply) nameNode.replaceWithText(Node.isStringLiteral(nameNode) ? JSON.stringify(propertyMap[text]) : propertyMap[text]);
      }
    }
  }
  // corpos de requisição montados nos serviços: `{ codigo }`, `{ texto: ajuste, versaoBase }`
  if (isService) {
    for (const assignment of file.getDescendantsOfKind(SyntaxKind.PropertyAssignment)) {
      const nameNode = assignment.getNameNode();
      const text = nameNode.getText();
      if (!SCREAMING.test(text) && Object.hasOwn(propertyMap, text) && !nameNode.getSymbol()) {
        report.literais++;
        if (apply) nameNode.replaceWithText(propertyMap[text]);
      }
    }
    for (const shorthand of file.getDescendantsOfKind(SyntaxKind.ShorthandPropertyAssignment)) {
      const text = shorthand.getName();
      if (!SCREAMING.test(text) && Object.hasOwn(propertyMap, text)) {
        report.literais++;
        if (apply) shorthand.replaceWithText(`${propertyMap[text]}: ${text}`);
      }
    }
  }
  // marcadores do catálogo: msg('CHAVE', { busca: termo }) -> { search: termo }
  const markerMap = JSON.parse(readFileSync(join(tools, 'message-marker-map.json'), 'utf8'));
  for (const call of file.getDescendantsOfKind(SyntaxKind.CallExpression)) {
    if (call.getExpression().getText() !== 'msg') continue;
    const argument = call.getArguments()[1];
    if (!argument || !Node.isObjectLiteralExpression(argument)) continue;
    for (const property of argument.getProperties()) {
      if (Node.isPropertyAssignment(property)) {
        const name = property.getName();
        if (Object.hasOwn(markerMap, name)) {
          report.literais++;
          if (apply) property.getNameNode().replaceWithText(markerMap[name]);
        }
      } else if (Node.isShorthandPropertyAssignment(property)) {
        const name = property.getName();
        if (Object.hasOwn(markerMap, name)) {
          report.literais++;
          if (apply) property.replaceWithText(`${markerMap[name]}: ${name}`);
        }
      }
    }
  }
  // pedaços de template: `/alunos/${id}/relatorios-aula`
  for (const template of file.getDescendantsOfKind(SyntaxKind.TemplateExpression)) {
    const head = template.getHead();
    const headText = head.getLiteralText();
    if (/^[A-Z][A-Z0-9_]*_$/.test(headText) && ident.has(headText)) {
      report.literais++;
      if (apply) head.replaceWithText(head.getText().replace(headText, ident.get(headText)));
    }
    const pieces = [template.getHead(), ...template.getTemplateSpans().map((span) => span.getLiteral())];
    for (const piece of pieces) {
      const text = piece.getLiteralText();
      const looksLikePath = /^\//.test(text) || text.includes('/api/v1') || /^[A-Za-z0-9-]*\/[A-Za-z-]/.test(text) || /^\?/.test(text);
      if (!(isService || isApiArgument(template) || (isTest && template.getHead().getLiteralText().includes('/api/')) || (isTest && isUrlFragmentTest(template)))) continue;
      if (!looksLikePath && !/^\?|^&/.test(text)) continue;
      const novo = mapQuery(mapPath(text));
      if (novo !== text) {
        report.caminhos++;
        if (apply) piece.replaceWithText(piece.getText().replace(text, novo));
      }
    }
  }
}
if (apply) await project.save();

// ---- 4. acessos que o compilador não resolve (uniões estreitadas por `in`, JSON sem tipo, fixtures de teste) --------------
if (apply) {
  const second = new Project({ tsConfigFilePath: join(frontend, 'tsconfig.json') });
  report.acessos = 0;
  for (const file of second.getSourceFiles()) {
    const path = relative(frontend, file.getFilePath()).split(sep).join('/');
    if (!(path.startsWith('src/') || path.startsWith('tests/')) || path.endsWith('api-tipos.ts') || /language-guard/.test(path)) continue;
    for (const access of file.getDescendantsOfKind(SyntaxKind.PropertyAccessExpression)) {
      const nameNode = access.getNameNode();
      const name = nameNode.getText();
      if (SCREAMING.test(name) || !Object.hasOwn(propertyMap, name) || nameNode.getSymbol()) continue;
      nameNode.replaceWithText(propertyMap[name]);
      report.acessos++;
    }
  }
  await second.save();
}
console.log(`${apply ? 'aplicado' : 'candidato'}:`, JSON.stringify(report));
void explicit;
