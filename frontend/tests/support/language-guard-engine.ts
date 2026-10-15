// Motor do guardião de idioma do frontend (feature 003, FR-019 a FR-023 e FR-035; research.md R-05).
// Mesmas regras do guardião Java (GlossaryFile): casamento por palavra, exceções documentadas, texto para
// pessoas fora do alcance e exclusões temporárias por fatia. Usado por language-guard.test.ts e pelos autotestes.
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import ts from 'typescript';

export type Where = 'identifier' | 'literal' | 'path';

export interface Violation {
  path: string;
  line: number;
  kind: string;
  text: string;
  term: string;
  english: string;
  hint: string;
}

export interface GlossaryData {
  words: Record<string, string>;
  contextual?: { pt: string; en: string }[];
  exceptions: ExceptionData[];
}

interface ExceptionData {
  id: string;
  mode?: string;
  where?: string;
  terms?: string[];
  paths?: string[];
  pathsExcept?: string[];
  appliesTo?: string[];
  reason?: string;
  constitutionCategory?: string;
}

export interface ExclusionsData {
  files?: { path: string; slice?: number }[];
  terms?: { path: string; term: string; slice?: number }[];
}

interface ExceptionRule {
  id: string;
  mode: string;
  literalOnly: boolean;
  terms: string[];
  paths: RegExp[];
  pathsExcept: RegExp[];
  appliesTo: Set<string>;
  hits: number;
}

export interface Entry {
  path: string;
  directory: boolean;
  name: string;
}

export const GLOSSARY = '.specify/memory/glossary.json';
export const EXCLUSIONS = '.specify/memory/glossary-temporary-exclusions.json';

const IGNORED_DIRECTORIES = new Set([
  '.git',
  'node_modules',
  'target',
  'dist',
  'build',
  'playwright-report',
  'test-results',
  '.idea',
  '.vscode',
]);
const FRONTEND_CODE = /^frontend\/(src|tests|scripts|public)(\/.*)?$|^frontend\/[^/]+\.(ts|js|mjs|cjs)$/;
const FRONTEND_ARTIFACTS = /^frontend\/(package\.json|Dockerfile|index\.html|\.prettier[^/]*|src\/.*\.json)$/;

/** Raiz do repositório: GUARD_ROOT ou o pai da pasta frontend (o Vitest roda em frontend/; no contêiner, /work). */
export function repositoryRoot(): string {
  return process.env.GUARD_ROOT ?? resolve(process.cwd(), '..');
}

export function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '');
}

export function splitWords(text: string): string[] {
  const spaced = fold(text)
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2');
  return (spaced.match(/[A-Za-z]+/g) ?? []).map((word) => word.toLowerCase());
}

/** Converte um glob de caminho (com ** e *) em expressão regular. */
export function glob(pattern: string): RegExp {
  let regex = '';
  for (let i = 0; i < pattern.length; i++) {
    const c = pattern.charAt(i);
    if (c === '*') {
      if (pattern.charAt(i + 1) === '*') {
        const slash = pattern.charAt(i + 2) === '/';
        regex += slash ? '(?:.*/)?' : '.*';
        i += slash ? 2 : 1;
      } else {
        regex += '[^/]*';
      }
    } else if (c === '?') {
      regex += '[^/]';
    } else if (c === '[') {
      const end = pattern.indexOf(']', i);
      regex += pattern.slice(i, end + 1);
      i = end;
    } else {
      regex += c.replace(/[.+^${}()|\\]/g, '\\$&');
    }
  }
  return new RegExp(`^${regex}$`);
}

export function walk(root: string): Entry[] {
  const entries: Entry[] = [];
  const visit = (directory: string) => {
    for (const child of readdirSync(directory, { withFileTypes: true }).sort((a, b) =>
      a.name.localeCompare(b.name),
    )) {
      const full = join(directory, child.name);
      const path = relative(root, full).split(sep).join('/');
      if (child.isDirectory()) {
        if (IGNORED_DIRECTORIES.has(child.name)) continue;
        entries.push({ path, directory: true, name: child.name });
        visit(full);
      } else if (child.isFile()) {
        entries.push({ path, directory: false, name: child.name });
      }
    }
  };
  visit(root);
  return entries;
}

/** Escopo do guardião do frontend: código, testes, scripts e ativos públicos (nomes), e configurações .ts/.js. */
export function inScope(entry: Entry): boolean {
  return FRONTEND_CODE.test(entry.path) && !FRONTEND_ARTIFACTS.test(entry.path);
}

export function ownedByTypescriptGuard(path: string): boolean {
  return FRONTEND_CODE.test(path) && !FRONTEND_ARTIFACTS.test(path);
}

export function formatViolation(violation: Violation): string {
  const suggestion = violation.hint ? `${violation.english}${violation.hint}` : violation.english;
  return `${violation.path}:${violation.line}: "${violation.text}" contém "${violation.term}" → use "${suggestion}"`;
}

export class LanguageGuard {
  private readonly rules: ExceptionRule[];
  private readonly contextual = new Map<string, string[]>();
  private readonly excludedFiles = new Set<string>();
  private readonly excludedTerms = new Set<string>();
  private readonly textForPeopleRule: ExceptionRule | undefined;

  constructor(
    private readonly glossary: GlossaryData,
    exclusions: ExclusionsData | null = null,
  ) {
    for (const entry of glossary.contextual ?? []) {
      this.contextual.set(entry.pt, [...(this.contextual.get(entry.pt) ?? []), entry.en]);
    }
    this.rules = glossary.exceptions.map((data) => {
      if (!data.reason?.trim()) throw new Error(`Exceção "${data.id}" do glossário sem motivo (reason).`);
      if (!data.constitutionCategory?.trim()) {
        throw new Error(
          `Exceção "${data.id}" do glossário sem categoria do Princípio VI (constitutionCategory).`,
        );
      }
      if (!data.mode) throw new Error(`Exceção "${data.id}" do glossário sem mode.`);
      return {
        id: data.id,
        mode: data.mode,
        literalOnly: data.where === 'literal',
        terms: (data.terms ?? []).map((term) => fold(term).toLowerCase()),
        paths: (data.paths ?? []).map(glob),
        pathsExcept: (data.pathsExcept ?? []).map(glob),
        appliesTo: new Set(data.appliesTo ?? []),
        hits: 0,
      };
    });
    this.textForPeopleRule = this.rules.find((rule) => rule.mode === 'builtin');
    for (const file of exclusions?.files ?? []) this.excludedFiles.add(file.path);
    for (const term of exclusions?.terms ?? []) this.excludedTerms.add(`${term.path}\u0000${term.term}`);
  }

  static load(root: string): LanguageGuard {
    const glossary = JSON.parse(readFileSync(join(root, GLOSSARY), 'utf8')) as GlossaryData;
    let exclusions: ExclusionsData | null;
    try {
      exclusions = JSON.parse(readFileSync(join(root, EXCLUSIONS), 'utf8')) as ExclusionsData;
    } catch {
      exclusions = null;
    }
    return new LanguageGuard(glossary, exclusions);
  }

  /** Palavra portuguesa do glossário para um equivalente em inglês (autotestes sem literal em português). */
  portugueseFor(english: string): string {
    const found = Object.entries(this.glossary.words).find(([, value]) => value === english);
    if (!found) throw new Error(`Glossário sem equivalente de "${english}"`);
    return found[0];
  }

  private coversPath(rule: ExceptionRule, path: string): boolean {
    const included = rule.paths.length === 0 || rule.paths.some((pattern) => pattern.test(path));
    return included && !rule.pathsExcept.some((pattern) => pattern.test(path));
  }

  private lookup(word: string): { term: string; english: string; hint: string } | null {
    const candidates = [word];
    if (word.endsWith('s') && word.length > 2) candidates.push(word.slice(0, -1));
    if (word.endsWith('es') && word.length > 3) candidates.push(word.slice(0, -2));
    for (const candidate of candidates) {
      if (!Object.hasOwn(this.glossary.words, candidate)) continue;
      const english = this.glossary.words[candidate] as string;
      if (english.startsWith('(')) continue;
      const others = (this.contextual.get(candidate) ?? []).filter((value) => value !== english);
      const hint = others.length > 0 ? ` (ou "${others.join('", "')}", conforme o contexto)` : '';
      return { term: candidate, english, hint };
    }
    return null;
  }

  /** Termos do glossário num texto, depois de aplicar as exceções de termo que valem para o caminho. */
  examine(text: string, path: string, where: Where): { term: string; english: string; hint: string }[] {
    const working = fold(text);
    const lowered = working.toLowerCase();
    const masked = working.split('');
    for (const rule of this.rules) {
      if (rule.mode !== 'term' || !this.coversPath(rule, path)) continue;
      if (rule.literalOnly && where !== 'literal') continue;
      for (const term of rule.terms) {
        let from = 0;
        let at = lowered.indexOf(term, from);
        while (at >= 0) {
          rule.hits++;
          for (let i = at; i < at + term.length; i++) masked[i] = ' ';
          from = at + term.length;
          at = lowered.indexOf(term, from);
        }
      }
    }
    const found: { term: string; english: string; hint: string }[] = [];
    for (const word of splitWords(masked.join(''))) {
      const hit = this.lookup(word);
      if (hit) found.push(hit);
    }
    return found;
  }

  violations(path: string, line: number, kind: string, text: string, where: Where): Violation[] {
    return this.examine(text, path, where).map((hit) => ({ path, line, kind, text, ...hit }));
  }

  isTextForPeople(literal: string): boolean {
    const text =
      /\s/.test(literal) ||
      [...literal].some((char) => char.charCodeAt(0) > 127) ||
      /^[A-Z][a-z]+[.!?:,;]*$/.test(literal);
    if (text && this.textForPeopleRule) this.textForPeopleRule.hits++;
    return text;
  }

  isSkippedFile(path: string): boolean {
    let skipped = false;
    for (const rule of this.rules) {
      if (rule.mode === 'skip-file' && rule.paths.length > 0 && this.coversPath(rule, path)) {
        rule.hits++;
        skipped = true;
      }
    }
    return skipped;
  }

  deadExceptions(guard: string): string[] {
    return this.rules
      .filter((rule) => rule.appliesTo.has(guard) && rule.hits === 0)
      .map((rule) => rule.id)
      .sort();
  }

  applyExclusions(
    found: Violation[],
    ownsPath: (path: string) => boolean,
  ): { remaining: Violation[]; dead: string[] } {
    const usedFiles = new Set<string>();
    const usedTerms = new Set<string>();
    const remaining: Violation[] = [];
    for (const violation of found) {
      const termKey = `${violation.path}\u0000${violation.term}`;
      if (this.excludedFiles.has(violation.path)) usedFiles.add(violation.path);
      else if (this.excludedTerms.has(termKey)) usedTerms.add(termKey);
      else remaining.push(violation);
    }
    const dead: string[] = [];
    for (const path of this.excludedFiles) if (ownsPath(path) && !usedFiles.has(path)) dead.push(path);
    for (const key of this.excludedTerms) {
      const [path] = key.split('\u0000');
      if (path !== undefined && ownsPath(path) && !usedTerms.has(key)) dead.push(key.replace('\u0000', ' '));
    }
    remaining.sort((a, b) => a.path.localeCompare(b.path) || a.line - b.line || a.term.localeCompare(b.term));
    return { remaining, dead: dead.sort() };
  }

  /** Fecha uma execução: modo relatório grava o JSON; senão devolve TODOS os problemas (vazio = passa). */
  conclude(
    guard: string,
    reportFile: string,
    found: Violation[],
    ownsPath: (path: string) => boolean,
  ): string[] {
    if (process.env.GUARD_REPORT === '1') {
      mkdirSync(dirname(reportFile), { recursive: true });
      const rows = found.map((v) => ({
        path: v.path,
        line: v.line,
        kind: v.kind,
        text: v.text,
        term: v.term,
        suggestion: v.english,
      }));
      writeFileSync(reportFile, `${JSON.stringify(rows, null, 2)}\n`);
      return [];
    }
    const { remaining, dead } = this.applyExclusions(found, ownsPath);
    return [
      ...remaining.map(formatViolation),
      ...dead.map((entry) => `exclusão temporária morta (não casa com nada): ${entry}`),
      ...this.deadExceptions(guard).map((id) => `exceção do glossário morta (não casa com nada): ${id}`),
    ];
  }

  // -------------------------------------------------------------------------------------------------------
  // TypeScript, JSX e JavaScript
  // -------------------------------------------------------------------------------------------------------

  scanSource(path: string, content: string): Violation[] {
    const scriptKind = path.endsWith('.tsx')
      ? ts.ScriptKind.TSX
      : path.endsWith('.ts')
        ? ts.ScriptKind.TS
        : ts.ScriptKind.JS;
    const source = ts.createSourceFile(path, content, ts.ScriptTarget.Latest, true, scriptKind);
    const found: Violation[] = [];
    const lineOf = (node: ts.Node) => source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1;
    const literal = (node: ts.Node, text: string, kind: string) => {
      if (text !== '' && !this.isTextForPeople(text))
        found.push(...this.violations(path, lineOf(node), kind, text, 'literal'));
    };
    const classes = (node: ts.Node, text: string) => {
      for (const part of text.split(/\s+/)) {
        if (part) found.push(...this.violations(path, lineOf(node), 'class', part, 'identifier'));
      }
    };
    const visit = (node: ts.Node, inClassName: boolean) => {
      if (ts.isImportDeclaration(node) || (ts.isExportDeclaration(node) && node.moduleSpecifier)) {
        const specifier = node.moduleSpecifier;
        if (specifier && ts.isStringLiteral(specifier)) {
          found.push(...this.violations(path, lineOf(specifier), 'import', specifier.text, 'literal'));
        }
        return;
      }
      if (ts.isIdentifier(node) || ts.isPrivateIdentifier(node)) {
        found.push(...this.violations(path, lineOf(node), 'identifier', node.text, 'identifier'));
        return;
      }
      if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
        if (inClassName) classes(node, node.text);
        else literal(node, node.text, 'literal');
        return;
      }
      if (ts.isTemplateHead(node) || ts.isTemplateMiddle(node) || ts.isTemplateTail(node)) {
        if (inClassName) classes(node, node.text);
        else literal(node, node.text, 'literal');
        return;
      }
      const className =
        inClassName ||
        (ts.isJsxAttribute(node) && ts.isIdentifier(node.name) && node.name.text === 'className');
      ts.forEachChild(node, (child) => visit(child, className));
    };
    visit(source, false);
    return found;
  }

  // -------------------------------------------------------------------------------------------------------
  // CSS
  // -------------------------------------------------------------------------------------------------------

  scanCss(path: string, content: string): Violation[] {
    const found: Violation[] = [];
    const clean = content.replace(/\/\*[\s\S]*?\*\//g, (comment) => comment.replace(/[^\n]/g, ' '));
    let header = '';
    let depth = 0;
    let line = 1;
    let headerLine = 1;
    const declaration = (text: string, at: number) => {
      const colon = text.indexOf(':');
      if (colon < 0) return;
      const property = text.slice(0, colon).trim();
      const value = text.slice(colon + 1);
      found.push(...this.violations(path, at, 'property', property, 'identifier'));
      for (const match of text.matchAll(/var\(\s*(--[\w-]+)|url\(\s*['"]?([^'")\s]+)|(--[\w-]+)\s*:/g)) {
        const reference = match[1] ?? match[2] ?? match[3];
        if (reference && reference !== property)
          found.push(...this.violations(path, at, 'css-reference', reference, 'identifier'));
      }
      if (property === 'animation' || property === 'animation-name') {
        for (const part of value.trim().split(/\s+/)) {
          if (part && !/^\d/.test(part))
            found.push(...this.violations(path, at, 'animation', part, 'identifier'));
        }
      }
    };
    for (const c of clean) {
      if (c === '\n') line++;
      if (c === '{') {
        const selector = header.trim();
        if (selector) found.push(...this.violations(path, headerLine, 'selector', selector, 'identifier'));
        header = '';
        depth++;
      } else if (c === '}') {
        depth--;
        header = '';
      } else if (c === ';' && depth > 0) {
        declaration(header, headerLine);
        header = '';
      } else {
        if (header.trim() === '') headerLine = line;
        header += c;
      }
    }
    if (header.trim() !== '' && depth > 0) declaration(header, headerLine);
    return found;
  }

  /** Nome de arquivo ou pasta: examinado uma vez, no próprio item. */
  scanName(entry: Entry): Violation[] {
    return this.violations(entry.path, 1, entry.directory ? 'directory' : 'file', entry.name, 'path');
  }
}
