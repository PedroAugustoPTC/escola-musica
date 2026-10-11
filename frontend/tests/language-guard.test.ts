// Guardião de idioma do frontend (feature 003, FR-019 a FR-023): identificadores, nomes de propriedade e de atributo
// JSX, especificadores de import, classes em className, literais de uma palavra, partes estáticas de template
// literals (rotas), seletores, classes e propriedades customizadas de CSS e nomes de arquivos e pastas não podem
// conter termos do glossário em português (.specify/memory/glossary.json). O que ainda não migrou fica em
// .specify/memory/glossary-temporary-exclusions.json (só existe na branch da feature).
//
// Modo relatório: GUARD_REPORT=1 grava test-results/guard-violations.json em vez de reprovar.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  LanguageGuard,
  inScope,
  ownedByTypescriptGuard,
  repositoryRoot,
  walk,
  type Violation,
} from './support/language-guard-engine';

const root = repositoryRoot();

describe('guardião de idioma do frontend', () => {
  it('o código, os estilos e os nomes de arquivo do frontend usam só inglês (fora das exceções documentadas)', () => {
    const guard = LanguageGuard.load(root);
    const found: Violation[] = [];

    for (const entry of walk(root)) {
      // toda entrada passa pela regra skip-file (é assim que as exceções por pasta provam que estão em uso)
      if (guard.isSkippedFile(entry.path) || !inScope(entry)) continue;
      found.push(...guard.scanName(entry));
      if (entry.directory) continue;
      if (/\.(tsx?|jsx?|mjs|cjs)$/.test(entry.name)) {
        found.push(...guard.scanSource(entry.path, readFileSync(join(root, entry.path), 'utf8')));
      } else if (entry.name.endsWith('.css')) {
        found.push(...guard.scanCss(entry.path, readFileSync(join(root, entry.path), 'utf8')));
      }
    }

    const problems = guard.conclude(
      'typescript',
      join(root, 'frontend/test-results/guard-violations.json'),
      found,
      ownedByTypescriptGuard,
    );
    expect(
      problems,
      `Guardião de idioma (typescript): ${problems.length} problema(s)\n  ${problems.join('\n  ')}`,
    ).toEqual([]);
    // a varredura do repositório inteiro leva segundos em disco montado (Windows/contêiner)
  }, 120_000);
});
