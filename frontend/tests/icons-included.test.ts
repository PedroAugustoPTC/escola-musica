// Guardião dos ícones: todo nome de ícone usado no código precisa existir no subconjunto da fonte
// Material Symbols (src/styles/fonts/included-icons.txt). Nome fora da lista não tem glifo: o texto
// do nome (por exemplo "pause_circle") aparece em letras, estoura a largura e quebra o layout em 390 px.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { walk, repositoryRoot } from './support/language-guard-engine';

const root = repositoryRoot();
const SOURCE_DIR = 'frontend/src';
const INCLUDED_ICONS_FILE = 'frontend/src/styles/fonts/included-icons.txt';
// literal em posição de valor (valor direto, `{'x'}` ou ramo de ternário `? 'x' : 'y'`); o literal de uma
// condição (`type === 'ok' ? ...`) não é nome de ícone
const STRING_LITERAL = /(?:^|[?:{])\s*(['"`])([a-z][a-z0-9_]*)\1/g;

/** Devolve o trecho que começa em `start`, até o fim da expressão `{...}` balanceada (inclusive). */
function balancedExpression(source: string, start: number): string {
  let depth = 0;
  for (let index = start; index < source.length; index++) {
    if (source[index] === '{') depth++;
    if (source[index] === '}' && --depth === 0) return source.slice(start, index + 1);
  }
  return source.slice(start);
}

/** Valor do atributo logo após o `=` casado: a expressão `{...}` balanceada ou o literal entre aspas. */
function attributeValue(source: string, match: RegExpMatchArray): string {
  const at = (match.index ?? 0) + match[0].length;
  return source[at] === '{'
    ? balancedExpression(source, at)
    : (source.slice(at, at + 80).split(/\s/)[0] ?? '');
}

/** Nomes entre aspas encontrados em uma expressão (cobre ternários como `a ? 'x' : 'y'`). */
function literalNames(expression: string): string[] {
  return [...expression.matchAll(STRING_LITERAL)].map((match) => match[2] ?? '');
}

/**
 * Extrai os nomes de ícone referenciados em um arquivo-fonte. Padrões cobertos:
 * - `<Icon name="x" />` e `<Icon name={cond ? 'x' : 'y'} />`;
 * - propriedade `icon="x"` e `icon={cond ? 'x' : 'y'}` (Button, EmptyState, ConfirmationDialog...);
 * - propriedade de objeto `icon: 'x'` (itens de navegação e atalhos).
 */
function extractIconNames(source: string): string[] {
  const names: string[] = [];
  for (const match of source.matchAll(/<Icon\s[^>]*?\bname=(?=["{])/g)) {
    names.push(...literalNames(attributeValue(source, match)));
  }
  for (const match of source.matchAll(/\bicon=(?=["{])/g)) {
    names.push(...literalNames(attributeValue(source, match)));
  }
  for (const match of source.matchAll(/\bicon:\s*(['"`])([a-z][a-z0-9_]*)\1/g)) names.push(match[2] ?? '');
  return names;
}

function includedIcons(): Set<string> {
  const text = readFileSync(join(root, INCLUDED_ICONS_FILE), 'utf8');
  return new Set(
    text
      .split(',')
      .map((name) => name.trim())
      .filter(Boolean),
  );
}

describe('ícones usados no código constam no subconjunto da fonte', () => {
  it('o extrator reconhece os padrões de referência a ícone', () => {
    const sample = `
      <Icon name="a_one" />
      <Icon name={ok ? 'b_two' : 'c_three'} size="small" />
      <Button icon="d_four" />
      <Button icon={playing ? 'e_five' : 'f_six'} onClick={() => go()} />
      const ITEMS = [{ key: 'k', icon: 'g_seven' }];
      <Icon name={item.icon} />
      <Icon name={notice.type === 'cond_x' ? 'h_eight' : 'i_nine'} />
    `;
    expect(extractIconNames(sample).sort()).toEqual(
      ['a_one', 'b_two', 'c_three', 'd_four', 'e_five', 'f_six', 'g_seven', 'h_eight', 'i_nine'].sort(),
    );
  });

  it('todo nome de ícone usado em frontend/src está em included-icons.txt', () => {
    const included = includedIcons();
    const missing = new Map<string, string[]>();
    let total = 0;

    for (const entry of walk(join(root, SOURCE_DIR))) {
      if (entry.directory || !/\.tsx?$/.test(entry.name)) continue;
      const path = `${SOURCE_DIR}/${entry.path}`;
      for (const name of extractIconNames(readFileSync(join(root, path), 'utf8'))) {
        total++;
        if (!included.has(name)) missing.set(name, [...(missing.get(name) ?? []), path]);
      }
    }

    // proteção contra um extrator que parou de enxergar o código (o projeto usa dezenas de ícones)
    expect(total).toBeGreaterThan(40);
    expect([...missing.entries()].map(([name, files]) => `${name} (${files.join(', ')})`)).toEqual([]);
    // lê todos os arquivos de src/: em disco montado de contêiner passa dos 5 s do limite padrão
  }, 60_000);

  it('a lista de ícones não tem nome repetido nem fora do padrão', () => {
    const names = readFileSync(join(root, INCLUDED_ICONS_FILE), 'utf8')
      .split(',')
      .map((name) => name.trim());
    expect(names.every((name) => /^[a-z][a-z0-9_]*$/.test(name))).toBe(true);
    expect(new Set(names).size).toBe(names.length);
  });
});
