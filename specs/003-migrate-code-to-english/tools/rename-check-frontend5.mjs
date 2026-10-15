#!/usr/bin/env node
// Verificador do frontend na fatia 5 (tasks T158, T165): só NOMES mudaram, nenhum texto para pessoas, nenhum binário.
//
//   REPO_ROOT=/work node rename-check-frontend5.mjs <árvore-base> <árvore-de-trabalho>     (contêiner node:24-alpine)
//
// A árvore-base é o commit anterior (git archive). Pareia cada arquivo de `frontend/{src,tests,scripts,public}` com o novo
// caminho (mesma função da renomeação), e compara:
//   - binários e textos não-código (imagens, fontes, licenças): SHA-256 idêntico;
//   - TypeScript/TSX/JS: os tokens do compilador, um a um (mesmo tipo, mesma quantidade). Identificador trocado precisa estar no
//     mapa; literal trocado só pode mudar PALAVRAS de código (classe, rota, chave) pelo mapa; texto para pessoas (JSX, mensagens,
//     títulos de teste, qualquer literal com espaço ou acento) tem de ficar idêntico;
//   - CSS: o mesmo, por palavras (`.classe`, `--variável`, `url()`), sem tocar em valores.
// Contadores: pares fora do mapa · literais de texto alterados · arquivos sem par/sobrando · hashes diferentes · outras divergências.
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadNames } from './lib/slice5-names.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const [baseRoot, workRoot] = process.argv.slice(2).filter((arg) => !arg.startsWith('--')).map((path) => resolve(path));
const root = resolve(process.env.REPO_ROOT ?? workRoot);
const ts = createRequire(join(root, 'frontend/package.json'))('typescript');
const { config, idMap, classMap, varMap, literalMap, newPathOf } = loadNames(here);

// ---- tabelas permitidas ------------------------------------------------------------------------------------------------
const pairs = new Map(); // antigo → Set(novos), nível de palavra/token
const allow = (from, to) => {
  if (typeof from !== 'string' || typeof to !== 'string' || from === to) return;
  if (!pairs.has(from)) pairs.set(from, new Set());
  pairs.get(from).add(to);
};
for (const [from, to] of idMap) allow(from, to);
for (const [from, to] of literalMap) allow(from, to);
for (const [from, to] of classMap) allow(from, to);
for (const [from, to] of varMap) allow(`--${from}`, `--${to}`);
for (const [from, to] of Object.entries(config.routeSegments)) allow(from, to);
for (const [from, to] of Object.entries(config.routeParams)) allow(from, to);
for (const [from, to] of Object.entries(config.queryParams)) allow(from, to);
for (const [from, to] of Object.entries(config.queryValues)) allow(from, to);
for (const [from, to] of Object.entries(config.literalOverrides)) allow(from, to);
for (const [from, to] of Object.entries(config.manualPairs ?? {})) allow(from, to);
for (const [key, to] of Object.entries(config.symbolOverrides ?? {})) allow(key.split(':')[1], to);
// partes de classe BEM: `menu-professor__item--ativo` ↔ `teacher-menu__item--active`
for (const [from, to] of classMap) {
  const a = from.split(/(__|--)/);
  const b = to.split(/(__|--)/);
  if (a.length === b.length) a.forEach((part, index) => allow(part, b[index]));
}
// palavras de caminho de ativo e de pasta (`fb-monograma-preto.png`)
for (const [from, to] of config.assetWords) allow(from.replace(/[./-]/g, ''), to.replace(/[./-]/g, ''));
for (const [from, to] of Object.entries(config.stemOverrides)) allow(from, to);
const folderWords = new Map(Object.entries(config.folderNames));
for (const [from, to] of folderWords) allow(from, to);

const relateWord = (from, to) => from === to || Boolean(pairs.get(from)?.has(to));
/** Token de código (identificador, palavra de caminho, classe, chave): igual, par do mapa, ou composto BEM/kebab de pares. */
function relateToken(from, to) {
  if (relateWord(from, to)) return true;
  for (const separator of [/(__|--)/, /(-)/, /(\.)/, /(_)/]) {
    const a = from.split(separator);
    const b = to.split(separator);
    if (a.length > 1 && a.length === b.length && a.every((part, index) => relateWord(part, b[index]))) return true;
  }
  return false;
}

/** Classes do CSS da árvore-base: as de `classMap` (renomeadas) e as que já eram inglês e ficam como estão. */
const oldClasses = new Set(classMap.keys());
function collectOldClasses(base) {
  for (const file of walk(join(base, 'frontend/src')).filter((name) => name.endsWith('.css'))) {
    const text = readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, ' ');
    for (const block of text.matchAll(/([^{}]+)\{/g)) for (const found of block[1].matchAll(/\.([A-Za-z_][\w-]*)/g)) oldClasses.add(found[1]);
  }
}
const isClassToken = (token) => oldClasses.has(token) || (/(--|__)$/.test(token) && oldClasses.has(token.replace(/(--|__)$/, '')));

/**
 * Compara o conteúdo de um literal. Só pode mudar PALAVRA DE CÓDIGO: string de classes, rota/consulta/caminho ou valor de uma palavra
 * (chave, id, valor de união). Texto para pessoas (espaço, acento, palavra Capitalizada) tem de ficar idêntico.
 */
function relateString(from, to) {
  if (from === to) return 'igual';
  from = from.replaceAll('%2F', '/');
  to = to.replaceAll('%2F', '/');
  const tokens = from.split(/\s+/).filter(Boolean);
  const nonAscii = [...from].some((char) => char.charCodeAt(0) > 127);
  const classLike = tokens.length > 0 && tokens.every(isClassToken);
  const pathLike = /^([/?]|\.\.?\/)|[?&][a-z]+=|%2F|\/marca\//.test(from) || /^[a-z]+=/.test(from);
  const singleCode = !/\s/.test(from) && !nonAscii && !/^[A-Z][a-z]+[.!?:,;]*$/.test(from);
  if (!(classLike || pathLike || singleCode)) return 'texto';
  const splitter = /([\s/?&=:%.,;()$#{}[\]'"`<>|+*~!@^\\]+)/;
  const a = from.split(splitter);
  const b = to.split(splitter);
  if (a.length !== b.length) return 'estrutura';
  for (let index = 0; index < a.length; index++) {
    if (index % 2 === 1) {
      if (a[index] !== b[index]) return 'separador';
    } else if (!relateToken(a[index], b[index])) return `par:${a[index]}→${b[index]}`;
  }
  return 'ok';
}

// ---- varredura ---------------------------------------------------------------------------------------------------------
const walk = (directory) =>
  existsSync(directory)
    ? readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
        const full = join(directory, entry.name);
        if (entry.isDirectory()) return ['node_modules', 'test-results', 'playwright-report', 'dist'].includes(entry.name) ? [] : walk(full);
        return [full];
      })
    : [];
const FOLDERS = ['src', 'tests', 'scripts', 'public'];
const listOf = (base) => FOLDERS.flatMap((folder) => walk(join(base, 'frontend', folder))).map((file) => relative(base, file).split(sep).join('/')).sort();
const GENERATED = /(api-tipos|api-types)\.ts$|(mensagens|messages)\.json$/;
const reviewedText = config.reviewedText ?? [];
const SKIPPED = new Set((config.manualFiles ?? []).map((entry) => entry.path)); // revisados à mão, registrados em evidence/slice-5.md

const sha = (file) => createHash('sha256').update(readFileSync(file)).digest('hex');
const report = { collapsed: 0, reviewed: 0, pairs: 0, outside: [], text: [], unpaired: [], hashes: [], other: [], comments: 0, swapped: 0, files: 0 };

function tokensOf(path, text) {
  const kind = path.endsWith('.tsx') ? ts.ScriptKind.TSX : path.endsWith('.mjs') || path.endsWith('.js') ? ts.ScriptKind.JS : ts.ScriptKind.TS;
  const source = ts.createSourceFile(path, text, ts.ScriptTarget.Latest, true, kind);
  const tokens = [];
  const visit = (node) => {
    if (node.kind >= ts.SyntaxKind.FirstJSDocNode && node.kind <= ts.SyntaxKind.LastJSDocNode) return; // comentário
    // `{' '}` é só espaçamento de JSX: o Prettier o move conforme a largura da linha
    if (node.kind === ts.SyntaxKind.JsxExpression && node.expression && ts.isStringLiteral(node.expression) && node.expression.text === ' ') return;
    const children = node.getChildren(source);
    if (children.length === 0) {
      // o Prettier reformata: vírgula final e parênteses em volta de JSX aparecem e somem conforme a largura da linha
      const parent = node.parent;
      if (node.kind === ts.SyntaxKind.CommaToken && parent) {
        const list = parent.getChildren(source).find((child) => child.kind === ts.SyntaxKind.SyntaxList && child.pos <= node.pos && node.end <= child.end);
        if (list && list.getChildren(source).at(-1) === node) return;
      }
      if (
        (node.kind === ts.SyntaxKind.OpenParenToken || node.kind === ts.SyntaxKind.CloseParenToken) &&
        parent?.kind === ts.SyntaxKind.ParenthesizedExpression &&
        ([ts.SyntaxKind.JsxElement, ts.SyntaxKind.JsxSelfClosingElement, ts.SyntaxKind.JsxFragment].includes(parent.expression.kind) ||
          parent.parent?.kind === ts.SyntaxKind.ReturnStatement)
      ) return;
      if (node.kind === ts.SyntaxKind.SemicolonToken && parent && [ts.SyntaxKind.PropertySignature, ts.SyntaxKind.MethodSignature].includes(parent.kind)) return;
      if (node.kind === ts.SyntaxKind.JsxText) {
        const normalized = node.getText(source).replace(/\s+/g, ' ').trim();
        if (!normalized) return;
        tokens.push({ kind: node.kind, text: normalized, line: source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1 });
        return;
      }
      if (node.kind !== ts.SyntaxKind.EndOfFileToken) tokens.push({ kind: node.kind, text: node.getText(source), line: source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1 });
    } else for (const child of children) visit(child);
  };
  visit(source);
  const comments = (text.match(/\/\*[\s\S]*?\*\/|(?<![:'"`])\/\/[^\n]*/g) ?? []).join('\n');
  return { tokens, comments };
}

const STRING_KINDS = new Set([
  ts.SyntaxKind.StringLiteral,
  ts.SyntaxKind.NoSubstitutionTemplateLiteral,
  ts.SyntaxKind.TemplateHead,
  ts.SyntaxKind.TemplateMiddle,
  ts.SyntaxKind.TemplateTail,
]);
const inner = (token) => {
  const text = token.text;
  if (token.kind === ts.SyntaxKind.StringLiteral) return text.slice(1, -1);
  if (token.kind === ts.SyntaxKind.NoSubstitutionTemplateLiteral) return text.slice(1, -1);
  if (token.kind === ts.SyntaxKind.TemplateHead) return text.slice(1, -2);
  if (token.kind === ts.SyntaxKind.TemplateMiddle) return text.slice(1, -2);
  return text.slice(1, -1);
};

function compareCode(oldRel, newRel, oldText, newText) {
  const a = tokensOf(oldRel, oldText);
  const b = tokensOf(newRel, newText);
  if (a.comments !== b.comments) report.comments++;
  let i = 0;
  let j = 0;
  while (i < a.tokens.length && j < b.tokens.length) {
    const from = a.tokens[i];
    const to = b.tokens[j];
    // `{ chave: valor }` virou `{ chave }`: a chave é do mapa e a variável passou a ter o mesmo nome
    if (
      from.kind === ts.SyntaxKind.Identifier && to.kind === ts.SyntaxKind.Identifier &&
      a.tokens[i + 1]?.kind === ts.SyntaxKind.ColonToken && a.tokens[i + 2]?.kind === ts.SyntaxKind.Identifier &&
      b.tokens[j + 1]?.kind !== ts.SyntaxKind.ColonToken &&
      relateToken(from.text, to.text) && relateToken(a.tokens[i + 2].text, to.text)
    ) {
      report.collapsed++;
      i += 3;
      j += 1;
      continue;
    }
    if (compareToken(newRel, from, to) === 'fatal') return; // o resto do arquivo ficaria desalinhado
    i++;
    j++;
  }
  if (i < a.tokens.length || j < b.tokens.length) report.other.push(`${newRel}: ${a.tokens.length} tokens antes, ${b.tokens.length} depois (sobram ${a.tokens.length - i}/${b.tokens.length - j})`);
}

function compareToken(newRel, from, to) {
  const where = `${newRel}:${to.line}`;
  if (from.kind !== to.kind) {
    report.other.push(`${where}: tipo de token ${ts.SyntaxKind[from.kind]} → ${ts.SyntaxKind[to.kind]} ("${from.text}" → "${to.text}")`);
    return 'fatal';
  }
  if (from.text === to.text) return;
  if (from.kind === ts.SyntaxKind.Identifier || from.kind === ts.SyntaxKind.PrivateIdentifier) {
    if (relateToken(from.text, to.text)) report.swapped++;
    else report.outside.push(`${where}: identificador "${from.text}" → "${to.text}"`);
  } else if (STRING_KINDS.has(from.kind)) {
    const verdict = relateString(inner(from), inner(to));
    if (verdict === 'ok') report.swapped++;
    else if (verdict === 'texto') {
      const reviewed = reviewedText.find((entry) => entry.path === newRel && entry.from === inner(from) && entry.to === inner(to));
      if (reviewed) report.reviewed++;
      else report.text.push(`${where}: ${JSON.stringify(inner(from))} → ${JSON.stringify(inner(to))}`);
    } else report.outside.push(`${where}: literal ${JSON.stringify(inner(from))} → ${JSON.stringify(inner(to))} (${verdict})`);
  } else if (from.kind === ts.SyntaxKind.RegularExpressionLiteral) {
    const verdict = relateString(from.text, to.text);
    if (verdict === 'ok') report.swapped++;
    else report.outside.push(`${where}: regex ${from.text} → ${to.text} (${verdict})`);
  } else if (from.kind === ts.SyntaxKind.JsxText) {
    report.text.push(`${where}: texto JSX ${JSON.stringify(from.text.trim())} → ${JSON.stringify(to.text.trim())}`);
  } else report.other.push(`${where}: "${from.text}" → "${to.text}"`);
}

function compareCss(oldRel, newRel, oldText, newText) {
  const strip = (text) => text.replace(/\/\*[\s\S]*?\*\//g, (comment) => comment.replace(/[^\n]/g, ' '));
  const comments = (text) => (text.match(/\/\*[\s\S]*?\*\//g) ?? []).join('\n');
  if (comments(oldText) !== comments(newText)) report.comments++;
  const lex = (text) => strip(text).match(/"[^"]*"|'[^']*'|[A-Za-z0-9_-]+|[^\sA-Za-z0-9_-]/g) ?? [];
  const a = lex(oldText);
  const b = lex(newText);
  if (a.length !== b.length) {
    report.other.push(`${newRel}: ${a.length} tokens CSS antes, ${b.length} depois`);
    return;
  }
  a.forEach((from, index) => {
    const to = b[index];
    if (from === to) return;
    if (/^["']/.test(from)) {
      const verdict = relateString(from.slice(1, -1), to.slice(1, -1));
      if (verdict === 'ok') report.swapped++;
      else report.text.push(`${newRel}: literal CSS ${from} → ${to}`);
    } else if (relateToken(from, to)) report.swapped++;
    else report.outside.push(`${newRel}: palavra CSS "${from}" → "${to}"`);
  });
}

collectOldClasses(baseRoot);
const baseFiles = listOf(baseRoot).filter((file) => !GENERATED.test(file));
const expected = new Set();
for (const oldRel of baseFiles) {
  const newRel = newPathOf(oldRel);
  expected.add(newRel);
  const oldFile = join(baseRoot, oldRel);
  const newFile = join(workRoot, newRel);
  if (!existsSync(newFile)) {
    report.unpaired.push(`${oldRel} → ${newRel} (não existe)`);
    continue;
  }
  report.pairs++;
  if (SKIPPED.has(newRel)) continue;
  report.files++;
  if (/\.(tsx?|mjs|js)$/.test(oldRel)) compareCode(oldRel, newRel, readFileSync(oldFile, 'utf8'), readFileSync(newFile, 'utf8'));
  else if (oldRel.endsWith('.css')) compareCss(oldRel, newRel, readFileSync(oldFile, 'utf8'), readFileSync(newFile, 'utf8'));
  else if (sha(oldFile) !== sha(newFile)) report.hashes.push(`${oldRel} → ${newRel}`);
}
for (const newRel of listOf(workRoot)) if (!expected.has(newRel) && !GENERATED.test(newRel)) report.unpaired.push(`${newRel} (sobrando: sem arquivo de origem)`);

const lines = [];
lines.push(`rename-check --frontend — fatia 5: ${report.pairs} arquivos pareados, ${report.files} comparados, ${report.swapped} trocas pelo mapa`);
lines.push(`${report.outside.length} pares fora do mapa`);
lines.push(`${report.text.length} literais/textos para pessoas alterados`);
lines.push(`${report.unpaired.length} arquivos sem par ou sobrando`);
lines.push(`${report.hashes.length} binários/arquivos não-código com SHA-256 diferente`);
lines.push(`${report.other.length} outras divergências (tipo ou quantidade de tokens)`);
lines.push(`(informativo) ${report.comments} arquivos com comentários alterados · ${report.collapsed} propriedades que viraram abreviadas · ${report.reviewed} texto(s) de erro de desenvolvimento revisado(s) em config.reviewedText · ${SKIPPED.size} arquivo(s) revisado(s) à mão (config.manualFiles)`);
for (const [title, list] of [['fora do mapa', report.outside], ['texto alterado', report.text], ['sem par', report.unpaired], ['hash diferente', report.hashes], ['outras', report.other]]) {
  if (list.length) lines.push(`\n== ${title} ==\n${list.slice(0, 120).join('\n')}${list.length > 120 ? `\n… +${list.length - 120}` : ''}`);
}
console.log(lines.join('\n'));
process.exit(report.outside.length || report.text.length || report.unpaired.length || report.hashes.length || report.other.length ? 1 : 0);
