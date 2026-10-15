#!/usr/bin/env node
// Renomeação do frontend na fatia 5 (tasks T153 a T157, T160): TypeScript/TSX/CSS, pelo mapa `maps/slice-5.json`.
// Roda no contêiner `node:24-alpine` com `ts-morph` instalado fora do package.json (`npm install --no-save ts-morph`).
//
//   REPO_ROOT=/work node specs/003-migrate-code-to-english/tools/ts-rename.mjs [--dry-run] [--phase symbols,names,literals,css,moves]
//
// Fases (nenhuma toca texto para pessoas: JSX, mensagens, títulos de teste e literais com espaço ou acento ficam como estão):
//  symbols   símbolos declarados no projeto (variáveis, funções, tipos, componentes, propriedades, props) renomeados com o
//            compilador, que acha todos os usos; colisão com nome já visível no escopo é registrada e o símbolo não muda
//  names     nomes de propriedade sem declaração (acessos a `Record<string, unknown>`, chaves de objeto sem tipo, desestruturação
//            de `useParams()`), pelo mesmo mapa; depois `{ a: a }` volta a ser `{ a }`
//  literals  literais de código: classes de `className`, valores de união e de comparação, chaves de cache, ids, parâmetros de
//            consulta, rotas da SPA (segmentos de caminho), ativos da marca
//  css       seletores (classes), propriedades personalizadas e `url()` dos arquivos .css
//  moves     arquivos e pastas (git mv) com reescrita dos especificadores relativos de import
// `--dry-run` não grava nada: imprime colisões, literais renomeados e literais de uma palavra não classificados.
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { loadNames } from './lib/slice5-names.mjs';

const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const dryRun = args.includes('--dry-run');
const phases = new Set((option('--phase', 'symbols,names,literals,css,moves') ?? '').split(','));
const root = resolve(process.env.REPO_ROOT ?? '/work');
const frontend = join(root, 'frontend');
const tools = join(root, 'specs/003-migrate-code-to-english/tools');
const requireFrom = createRequire(join(frontend, 'package.json'));
const { Project, SyntaxKind, Node, ts } = requireFrom('ts-morph');

const { config, idMap, classMap, varMap, literalMap, newPathOf } = loadNames(tools);

/** Arquivos versionados de `frontend/` (gerados no hospedeiro: `git ls-files frontend > frontend/test-results/tracked.txt`; o contêiner não tem git). */
const tracked = () => readFileSync(join(frontend, 'test-results/tracked.txt'), 'utf8').split('\n').filter(Boolean);

const OURS = (path) => {
  const rel = relative(frontend, path).split(sep).join('/');
  return /^(src|tests)\//.test(rel) && !/(api-tipos|api-types)\.ts$|language-guard/.test(rel) && !rel.includes('node_modules');
};

const project = new Project({ tsConfigFilePath: join(frontend, 'tsconfig.json') });
const ours = () => project.getSourceFiles().filter((file) => OURS(file.getFilePath()));
const report = { collisions: [], symbols: 0, names: 0, literals: [], unclassified: [], classTokens: 0, css: 0, moves: 0 };
const rel = (file) => relative(frontend, file.getFilePath()).split(sep).join('/');

// ---------------------------------------------------------------------------------------------------------------------
// symbols
// ---------------------------------------------------------------------------------------------------------------------
const checker = () => project.getTypeChecker();
const meaning = ts.SymbolFlags.Value | ts.SymbolFlags.Type | ts.SymbolFlags.Namespace;

function declarationNameOf(identifier) {
  const parent = identifier.getParent();
  if (!parent) return false;
  if (Node.isBindingElement(parent)) return parent.getNameNode() === identifier;
  if (Node.isImportSpecifier(parent) || Node.isExportSpecifier(parent)) return false; // o símbolo vem da origem
  if (Node.isShorthandPropertyAssignment(parent)) return false;
  return typeof parent.getNameNode === 'function' && parent.getNameNode() === identifier && !Node.isJsxAttribute(parent);
}

function isMemberLike(parent) {
  return (
    Node.isPropertySignature(parent) ||
    Node.isPropertyDeclaration(parent) ||
    Node.isMethodSignature(parent) ||
    Node.isMethodDeclaration(parent) ||
    Node.isPropertyAssignment(parent) ||
    Node.isEnumMember(parent) ||
    Node.isGetAccessorDeclaration(parent) ||
    Node.isSetAccessorDeclaration(parent)
  );
}

const GLOBAL_NAMES = new Set(['Object', 'Text', 'Parameters', 'Record', 'Array', 'Map', 'Set', 'String', 'Number', 'Boolean', 'Date', 'Error', 'Promise', 'Event', 'Node', 'Element', 'Document', 'Window', 'File', 'Blob', 'URL', 'Image', 'Option', 'Range', 'Selection', 'History', 'Location', 'Response', 'Request', 'Headers', 'Screen', 'Audio', 'new', 'default', 'delete', 'class', 'function', 'var', 'let', 'const', 'in', 'of', 'return', 'switch', 'case', 'catch', 'try', 'throw', 'void', 'this', 'type', 'enum', 'export', 'import']);

function collides(identifier, newName) {
  if (GLOBAL_NAMES.has(newName) && newName !== 'type') return true;
  const parent = identifier.getParent();
  if (isMemberLike(parent)) {
    const owner = parent.getParent();
    if (Node.isObjectLiteralExpression(owner)) return owner.getProperties().some((p) => p !== parent && p.getName?.() === newName);
    if (Node.isInterfaceDeclaration(owner) || Node.isClassDeclaration(owner)) {
      const type = owner.getType();
      return Boolean(type.getProperty(newName)) || owner.getMembers().some((m) => m !== parent && m.getName?.() === newName);
    }
    if (Node.isTypeLiteral(owner)) return owner.getMembers().some((m) => m !== parent && m.getName?.() === newName);
    if (Node.isEnumDeclaration(owner)) return owner.getMembers().some((m) => m !== parent && m.getName() === newName);
    return false;
  }
  const symbol = identifier.getSymbol();
  const raw = checker().compilerObject;
  const locations = [identifier, ...(identifier.findReferencesAsNodes?.() ?? [])];
  for (const location of locations) {
    const found = raw.resolveName(newName, location.compilerNode, meaning, true);
    if (found && (!symbol || found !== symbol.compilerSymbol)) return true;
  }
  // nome já declarado ao lado (parâmetros e variáveis do mesmo escopo)
  return false;
}

function runSymbols() {
  const skipped = new Set();
  const attempts = new Map();
  let guard = 0;
  for (;;) {
    let target = null;
    for (const file of ours()) {
      for (const identifier of file.getDescendantsOfKind(SyntaxKind.Identifier)) {
        const name = identifier.getText();
        if (!idMap.has(name) || !declarationNameOf(identifier)) continue;
        const key = `${rel(file)}:${identifier.getStart()}`;
        if (skipped.has(key)) continue;
        target = { identifier, key, name, file };
        break;
      }
      if (target) break;
    }
    if (!target) break;
    const newName = config.symbolOverrides?.[`${rel(target.file)}:${target.name}`] ?? idMap.get(target.name);
    if (collides(target.identifier, newName)) {
      report.collisions.push(`${rel(target.file)}:${target.identifier.getStartLineNumber()} ${target.name} → ${newName}`);
      skipped.add(target.key);
      continue;
    }
    if (dryRun) {
      skipped.add(target.key); // a execução a seco só lista
      report.symbols++;
      continue;
    }
    const attempt = `${rel(target.file)}:${target.name}:${target.identifier.getStartLineNumber()}`;
    // vários símbolos distintos com o mesmo nome podem estar na mesma linha; só desiste quando insiste demais
    attempts.set(attempt, (attempts.get(attempt) ?? 0) + 1);
    if (attempts.get(attempt) > 8) {
      report.collisions.push(`sem efeito (o nome continua igual): ${attempt} → ${newName}`);
      skipped.add(target.key);
      continue;
    }
    target.identifier.rename(newName, { usePrefixAndSuffixText: true });
    report.symbols++;
    if (++guard > 5000) throw new Error('laço de renomeação sem fim');
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// names
// ---------------------------------------------------------------------------------------------------------------------
function hasProjectDeclaration(identifier) {
  const symbol = identifier.getSymbol();
  const declarations = symbol?.getDeclarations() ?? [];
  return declarations.some((declaration) => {
    const path = declaration.getSourceFile().getFilePath();
    return !path.includes('node_modules') && !declaration.getSourceFile().isDeclarationFile();
  });
}

function runNames() {
  for (const file of ours()) {
    const edits = [];
    for (const identifier of file.getDescendantsOfKind(SyntaxKind.Identifier)) {
      const name = identifier.getText();
      if (!idMap.has(name)) continue;
      const parent = identifier.getParent();
      const asProperty =
        (Node.isPropertyAccessExpression(parent) && parent.getNameNode() === identifier) ||
        (Node.isPropertyAssignment(parent) && parent.getNameNode() === identifier) ||
        (Node.isBindingElement(parent) && parent.getPropertyNameNode() === identifier);
      if (!asProperty) continue;
      if (hasProjectDeclaration(identifier)) continue;
      edits.push([identifier.getStart(), identifier.getEnd(), idMap.get(name)]);
    }
    if (!edits.length) continue;
    report.names += edits.length;
    if (dryRun) continue;
    let text = file.getFullText();
    for (const [start, end, value] of edits.sort((a, b) => b[0] - a[0])) text = text.slice(0, start) + value + text.slice(end);
    file.replaceWithText(text);
  }
  if (dryRun) return;
  // `{ a: a }` volta a `{ a }` (só onde o nome é um dos nomes novos)
  const targets = new Set(idMap.values());
  for (const file of ours()) {
    const edits = [];
    for (const element of file.getDescendantsOfKind(SyntaxKind.BindingElement)) {
      const property = element.getPropertyNameNode();
      if (property && Node.isIdentifier(property) && property.getText() === element.getName() && targets.has(property.getText())) {
        const initializer = element.getInitializer();
        edits.push([element.getStart(), element.getEnd(), initializer ? `${element.getName()} = ${initializer.getText()}` : element.getName()]);
      }
    }
    for (const assignment of file.getDescendantsOfKind(SyntaxKind.PropertyAssignment)) {
      const initializer = assignment.getInitializer();
      if (initializer && Node.isIdentifier(initializer) && initializer.getText() === assignment.getName() && targets.has(assignment.getName())) {
        edits.push([assignment.getStart(), assignment.getEnd(), assignment.getName()]);
      }
    }
    if (!edits.length) continue;
    let text = file.getFullText();
    for (const [start, end, value] of edits.sort((a, b) => b[0] - a[0])) text = text.slice(0, start) + value + text.slice(end);
    file.replaceWithText(text);
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// literals
// ---------------------------------------------------------------------------------------------------------------------
const routeSegments = new Map(Object.entries(config.routeSegments));
const routeParams = new Map(Object.entries(config.routeParams));
const assetWords = config.assetWords;
const queryParams = new Map(Object.entries(config.queryParams));
const queryValues = new Map(Object.entries(config.queryValues));

/** Troca os segmentos de rota da SPA e as palavras de ativo de marca num texto de caminho. */
function rewritePath(text) {
  let changed = text;
  // segmentos: `/alunos`, `/alunos/`, `/alunos?`, `/alunos${…}` (o fim do texto também fecha o segmento)
  changed = changed.replace(/(^|\/)([a-z][a-z0-9-]*)(?=$|[/?#:]|\$\{)/g, (all, lead, word) =>
    routeSegments.has(word) ? `${lead}${routeSegments.get(word)}` : all,
  );
  changed = changed.replace(/%2F([a-z][a-z0-9-]*)/g, (all, word) => (routeSegments.has(word) ? `%2F${routeSegments.get(word)}` : all));
  changed = changed.replace(/:([A-Za-z]+)/g, (all, word) => (routeParams.has(word) ? `:${routeParams.get(word)}` : all));
  changed = changed.replace(/(^|[?&])([a-z]+)=([A-Za-z0-9_]*)/g, (all, lead, key, value) =>
    queryParams.has(key) ? `${lead}${queryParams.get(key)}=${queryValues.get(value) ?? value}` : all,
  );
  for (const [from, to] of assetWords) changed = changed.split(from).join(to);
  return changed;
}

function isRouteText(text) {
  return /^\/[A-Za-z0-9:/_?=&.${}-]*$/.test(text) && text !== '/' && !text.startsWith('/api/');
}

function mapClassToken(token) {
  if (classMap.has(token)) return classMap.get(token);
  const edge = /^(.*?)(--|__)$/.exec(token); // prefixo de classe montada: `botao--${variante}`
  if (edge && classMap.has(edge[1])) return `${classMap.get(edge[1])}${edge[2]}`;
  return null;
}

function isClassString(text) {
  const tokens = text.split(/\s+/).filter(Boolean);
  return tokens.length > 0 && tokens.every((token) => mapClassToken(token) !== null || /^[a-z][a-z0-9_-]*$/.test(token)) && tokens.some((token) => mapClassToken(token) !== null);
}

function insideNode(node, predicate) {
  for (let current = node.getParent(); current; current = current.getParent()) if (predicate(current)) return true;
  return false;
}

function isImportLike(node) {
  const parent = node.getParent();
  return Boolean(parent && (Node.isImportDeclaration(parent) || Node.isExportDeclaration(parent) || Node.isExternalModuleReference(parent) || (Node.isCallExpression(parent) && parent.getExpression().getText() === 'import')));
}

function isTestTitle(node) {
  const call = node.getParent();
  return Boolean(call && Node.isCallExpression(call) && call.getArguments()[0] === node && /^(describe|it|test|test\.describe|test\.step|test\.skip)(\.\w+)*$/.test(call.getExpression().getText()));
}

function rewriteLiteral(node, text, file) {
  if (isImportLike(node) || isTestTitle(node)) return null;
  const inClassName = insideNode(node, (n) => Node.isJsxAttribute(n) && n.getNameNode().getText() === 'className');
  if (inClassName || isClassString(text)) {
    const tokens = text.split(/(\s+)/);
    let hits = 0;
    const out = tokens.map((token) => {
      const mapped = token.trim() ? mapClassToken(token) : null;
      if (mapped !== null && mapped !== token) {
        hits++;
        return mapped;
      }
      return token;
    });
    if (hits) {
      report.classTokens += hits;
      return out.join('');
    }
    if (inClassName) return null;
  }
  // seletor CSS em texto (`closest('.estado-vazio')`, `querySelector('.alerta--erro')`)
  if (/(^|[\s>+~,(])\.[A-Za-z_]/.test(text) && !/\s[a-zà-ú]{3,}\s/.test(text)) {
    const changed = text.replace(/(^|[\s>+~,(])\.([A-Za-z_][\w-]*)/g, (all, lead, token) => (classMap.has(token) ? `${lead}.${classMap.get(token)}` : all));
    if (changed !== text) return changed;
  }
  if (/\s/.test(text) && !isRouteText(text.trim())) return null;
  if (isRouteText(text) || /\/marca\/|fb-/.test(text) || /(^|[?&])(busca|foco|registrar|motivo|destino)=/.test(text)) {
    const changed = rewritePath(text);
    return changed === text ? null : changed;
  }
  if (/^[a-z][a-z0-9-]*$/.test(text)) {
    if (literalMap.has(text)) return literalMap.get(text);
    return null;
  }
  // chave camelCase do documento JSON (ex.: `parte(conteudo, 'frequenciaEEstudo')`): mesmo nome do fio
  if (/^[a-z][A-Za-z0-9]*$/.test(text) && idMap.has(text) && !config.literalDeny.includes(text)) return idMap.get(text);
  return null;
}

function runLiterals() {
  for (const file of ours()) {
    const edits = [];
    const note = (node, from, to) => report.literals.push(`${rel(file)}:${node.getStartLineNumber()} ${JSON.stringify(from)} → ${JSON.stringify(to)}`);
    for (const node of file.getDescendantsOfKind(SyntaxKind.StringLiteral)) {
      const text = node.getLiteralText();
      const changed = rewriteLiteral(node, text, file);
      if (changed !== null && changed !== text) {
        edits.push([node.getStart() + 1, node.getEnd() - 1, changed]);
        note(node, text, changed);
      } else if (/^[a-z][a-z0-9-]*$/.test(text) && !isImportLike(node) && !isTestTitle(node) && isGlossaryWord(text)) {
        report.unclassified.push(`${rel(file)}:${node.getStartLineNumber()} ${JSON.stringify(text)}`);
      }
    }
    for (const node of file.getDescendantsOfKind(SyntaxKind.NoSubstitutionTemplateLiteral)) {
      const text = node.getLiteralText();
      const changed = rewriteLiteral(node, text, file);
      if (changed !== null && changed !== text) {
        edits.push([node.getStart() + 1, node.getEnd() - 1, changed]);
        note(node, text, changed);
      }
    }
    for (const kind of [SyntaxKind.TemplateHead, SyntaxKind.TemplateMiddle, SyntaxKind.TemplateTail]) {
      for (const node of file.getDescendantsOfKind(kind)) {
        const text = node.getLiteralText();
        const changed = rewriteLiteral(node, text, file);
        if (changed !== null && changed !== text) {
          // cabeça/meio/cauda: o texto bruto começa em "`" ou "}" e termina em "`" ou "${"
          const start = node.getStart() + 1;
          const end = node.getEnd() - (kind === SyntaxKind.TemplateTail ? 1 : 2);
          edits.push([start, end, changed]);
          note(node, text, changed);
        }
      }
    }
    for (const node of file.getDescendantsOfKind(SyntaxKind.RegularExpressionLiteral)) {
      const text = node.getText();
      const changed = text.replace(/(\\\/)([a-z][a-z0-9-]*)/g, (all, slash, word) => (routeSegments.has(word) ? `${slash}${routeSegments.get(word)}` : all));
      if (changed !== text) {
        edits.push([node.getStart(), node.getEnd(), changed]);
        note(node, text, changed);
      }
    }
    if (!edits.length || dryRun) continue;
    let content = file.getFullText();
    for (const [start, end, value] of edits.sort((a, b) => b[0] - a[0])) content = content.slice(0, start) + value + content.slice(end);
    file.replaceWithText(content);
  }
}

const glossaryWords = new Set([...idMap.keys(), ...classMap.keys()].filter((word) => /^[a-z][a-z0-9-]*$/.test(word)));
function isGlossaryWord(text) {
  return glossaryWords.has(text) && !literalMap.has(text);
}

// ---------------------------------------------------------------------------------------------------------------------
// css
// ---------------------------------------------------------------------------------------------------------------------
function runCss() {
  const folder = existsSync(join(frontend, 'src/styles')) ? 'src/styles' : 'src/estilos';
  const files = readdirSync(join(frontend, folder), { recursive: true })
    .filter((name) => String(name).endsWith('.css'))
    .map((name) => `frontend/${folder}/${String(name).split(sep).join('/')}`);
  for (const name of files) {
    const path = join(root, name);
    const original = readFileSync(path, 'utf8');
    let out = '';
    let header = '';
    let depth = 0;
    let inComment = false;
    // percorre o texto: cabeçalhos (seletores) fora de `{}` e corpo das regras tratados em separado
    let i = 0;
    const flushHeader = () => {
      out += header.replace(/\.([A-Za-z_][A-Za-z0-9_-]*)/g, (all, token) => {
        if (/^\s*@/.test(header)) return all;
        if (classMap.has(token)) {
          report.css++;
          return `.${classMap.get(token)}`;
        }
        return all;
      });
      header = '';
    };
    while (i < original.length) {
      const c = original[i];
      if (inComment) {
        out += c;
        if (c === '*' && original[i + 1] === '/') {
          out += '/';
          i++;
          inComment = false;
        }
        i++;
        continue;
      }
      if (c === '/' && original[i + 1] === '*') {
        flushHeaderIfAny();
        out += '/*';
        i += 2;
        inComment = true;
        continue;
      }
      if (c === '{') {
        flushHeader();
        out += c;
        depth++;
      } else if (c === '}') {
        flushHeaderIfAny();
        out += c;
        depth--;
      } else if (c === ';' && depth > 0) {
        header += c;
        flushDeclaration();
      } else header += c;
      i++;
    }
    flushHeaderIfAny();
    function flushHeaderIfAny() {
      if (!header) return;
      // texto solto antes de `}` é declaração; antes de `{` é seletor (tratado no ponto de `{`)
      flushDeclaration();
    }
    function flushDeclaration() {
      let text = header;
      header = '';
      text = text.replace(/--([A-Za-z][A-Za-z0-9-]*)/g, (all, token) => {
        if (varMap.has(token)) {
          report.css++;
          return `--${varMap.get(token)}`;
        }
        return all;
      });
      text = text.replace(/url\(\s*(['"]?)([^)'"]+)\1\s*\)/g, (all, quote, url) => {
        const changed = rewritePath(url).replace(/\/fontes\//g, '/fonts/').replace(/^\.\/fontes\//, './fonts/');
        return changed === url ? all : `url(${quote}${changed}${quote})`;
      });
      out += text;
    }
    if (!dryRun && out !== original) writeFileSync(path, out);
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// moves
// ---------------------------------------------------------------------------------------------------------------------
function runMoves() {
  const files = tracked();
  const fileMap = new Map(); // caminho antigo (absoluto) → novo (absoluto)
  for (const file of files) {
    const target = newPathOf(file);
    if (target !== file) fileMap.set(join(root, file), join(root, target));
  }
  const newOf = (absolute) => fileMap.get(absolute) ?? absolute;
  const isFile = (path) => existsSync(path) && statSync(path).isFile();
  const resolveTarget = (from, specifier) => {
    const base = resolve(dirname(from), specifier);
    return [base, `${base}.ts`, `${base}.tsx`, `${base}.js`, `${base}.mjs`, join(base, 'index.ts'), join(base, 'index.tsx')].find(isFile) ?? null;
  };
  // 1. reescreve os especificadores relativos (import/export e qualquer literal "./…" que aponte para um arquivo, como vi.mock)
  const rewrites = [];
  for (const file of project.getSourceFiles()) {
    const path = file.getFilePath();
    if (path.includes('node_modules') || !OURS(path) && !/(vite|playwright)\.config\.ts$/.test(path)) continue;
    const edits = [];
    for (const literal of file.getDescendantsOfKind(SyntaxKind.StringLiteral)) {
      const value = literal.getLiteralText();
      if (!/^\.\.?\//.test(value)) continue;
      const target = resolveTarget(path, value);
      if (!target) continue;
      const hasExtension = /\.(css|json|mjs|js|png|woff2?|txt)$/.test(value);
      const newPath = newOf(target);
      const newFrom = newOf(path);
      let next = relative(dirname(newFrom), newPath).split(sep).join('/');
      if (!hasExtension) next = next.replace(/\.(tsx?|jsx?)$/, '').replace(/\/index$/, '');
      if (!next.startsWith('.')) next = `./${next}`;
      if (next !== value) edits.push([literal.getStart() + 1, literal.getEnd() - 1, next]);
    }
    if (edits.length) rewrites.push([path, edits]);
  }
  if (dryRun) {
    report.moves = fileMap.size;
    for (const [from, to] of fileMap) report.literals.push(`mover ${relative(root, from)} → ${relative(root, to)}`);
    return;
  }
  for (const [path, edits] of rewrites) {
    let text = readFileSync(path, 'utf8');
    for (const [start, end, value] of edits.sort((a, b) => b[0] - a[0])) text = text.slice(0, start) + value + text.slice(end);
    writeFileSync(path, text);
  }
  // 2. git mv de cada arquivo
  for (const [from, to] of fileMap) {
    mkdirSync(dirname(to), { recursive: true });
    renameSync(from, to);
    report.moves++;
  }
}

// ---------------------------------------------------------------------------------------------------------------------
if (phases.has('symbols')) {
  runSymbols();
  if (!dryRun) project.saveSync();
}
if (phases.has('names')) {
  runNames();
  if (!dryRun) project.saveSync();
}
if (phases.has('literals')) {
  runLiterals();
  if (!dryRun) project.saveSync();
}
if (phases.has('css')) runCss();
if (phases.has('moves')) {
  if (!dryRun) project.saveSync();
  runMoves();
}

const lines = (title, list) => (list.length ? `\n== ${title} (${list.length}) ==\n${list.join('\n')}` : '');
console.log(
  `ts-rename${dryRun ? ' (execução a seco)' : ''}: ${report.symbols} símbolos, ${report.names} nomes sem declaração, ${report.literals.length} literais, ` +
    `${report.classTokens} classes em strings, ${report.css} trocas em CSS, ${report.moves} arquivos movidos, ${report.collisions.length} colisões`,
);
console.log(lines('colisões', report.collisions) + lines('literais', report.literals) + lines('literais de uma palavra do glossário não classificados', report.unclassified));
