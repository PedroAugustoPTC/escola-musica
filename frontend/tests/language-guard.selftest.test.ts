// Autotestes do guardião de idioma do frontend (feature 003, US2 cenário 2): alimentam o guardião com fontes
// sintéticas contendo um termo do glossário em cada tipo de artefato do frontend (rota, classe CSS, propriedade
// customizada, componente, prop JSX, arquivo, pasta, chave de cache) e exigem a falha com arquivo, termo e
// equivalente; e com exceções e texto para pessoas, que exigem o contrário.
//
// Os termos em português vêm do próprio glossário (equivalente em inglês → palavra portuguesa), então este
// arquivo não contém literal em português e nasce limpo para o guardião.
import { describe, expect, it } from 'vitest';
import {
  LanguageGuard,
  formatViolation,
  inScope,
  repositoryRoot,
  type Entry,
  type GlossaryData,
  type Violation,
} from './support/language-guard-engine';

const root = repositoryRoot();
const guard = LanguageGuard.load(root);
const pt = (english: string) => guard.portugueseFor(english);
const capitalize = (word: string) => word.charAt(0).toUpperCase() + word.slice(1);
const source = (code: string, path = 'frontend/src/Sample.tsx') => guard.scanSource(path, code);

function expectFlagged(found: Violation[], english: string, line: number) {
  const hit = found.find((violation) => violation.english === english && violation.line === line);
  expect(hit, `esperava "${english}" na linha ${line}; achou ${JSON.stringify(found)}`).toBeDefined();
  expect(formatViolation(hit as Violation)).toMatch(
    new RegExp(`:${line}: ".+" contém ".+" → use "${english}`),
  );
}

describe('autotestes do guardião de idioma do frontend', () => {
  it('reprova componente, prop JSX, variável, rota e chave de cache em português', () => {
    const student = pt('student');
    const code = [
      `export function ${capitalize(student)}Card({ ${student}Id }: { ${student}Id: string }) {`,
      `  const key = ['${student}s', ${student}Id];`,
      '  const route = `/' + student + 's/${' + student + 'Id}/new`;',
      `  return <div data-testid="${student}-card" ${student}Id={${student}Id}>{key.length + route.length}</div>;`,
      '}',
    ].join('\n');
    const found = source(code);
    expectFlagged(found, 'student', 1);
    expectFlagged(found, 'student', 2);
    expectFlagged(found, 'student', 3);
    expectFlagged(found, 'student', 4);
  });

  it('reprova rota com : e ? e cada classe de className', () => {
    const student = pt('student');
    const code = [
      `const path = '/${student}s/:${student}Id?${pt('search')}=a';`,
      `const view = <p className="card ${student}-name">oi</p>;`,
    ].join('\n');
    const found = source(code);
    expect(found.filter((violation) => violation.line === 1).map((violation) => violation.english)).toEqual(
      expect.arrayContaining(['student', 'search']),
    );
    expectFlagged(found, 'student', 2);
    expect(found.find((violation) => violation.kind === 'class')?.text).toBe(`${student}-name`);
  });

  it('reprova especificador de import em português, mas não os nomes importados', () => {
    const student = pt('student');
    const found = source(`import { Something } from './${student}s/Something';\nexport const x = Something;`);
    expectFlagged(found, 'student', 1);
    expect(found.filter((violation) => violation.kind === 'identifier' && violation.line === 1)).toEqual([]);
  });

  it('aceita texto para pessoas, comentários e texto de JSX', () => {
    const student = pt('student');
    const code = [
      `// ${student} em comentário`,
      `/** ${student} em javadoc */`,
      `const label = 'Texto sobre o ${student}';`,
      `const single = '${capitalize(student)}';`,
      `const accented = '${student}çã';`,
      `const view = <p>${student} dentro do JSX</p>;`,
    ].join('\n');
    expect(source(code)).toEqual([]);
  });

  it('reprova seletor, classe, propriedade customizada e referência var() em CSS', () => {
    const student = pt('student');
    const css = [
      `.${student}-card {`,
      `  --${student}-gap: 4px;`,
      `  margin: var(--${student}-gap);`,
      '}',
      `/* .${student} em comentário */`,
    ].join('\n');
    const found = guard.scanCss('frontend/src/styles/card.css', css);
    expectFlagged(found, 'student', 1);
    expectFlagged(found, 'student', 2);
    expectFlagged(found, 'student', 3);
    expect(found.every((violation) => violation.line <= 3)).toBe(true);
  });

  it('reprova nome de arquivo e de pasta, e só o escopo do frontend', () => {
    const student = pt('student');
    const directory: Entry = { path: `frontend/src/${student}s`, directory: true, name: `${student}s` };
    const file: Entry = { path: `frontend/src/${student}s/Card.tsx`, directory: false, name: 'Card.tsx' };
    const named: Entry = {
      path: 'frontend/src/Card.tsx',
      directory: false,
      name: `${capitalize(student)}Card.tsx`,
    };
    expect(guard.scanName(directory)).not.toEqual([]);
    expect(guard.scanName(file)).toEqual([]); // o arquivo é limpo: só a pasta é reportada, uma vez
    expect(guard.scanName(named)[0]?.english).toBe('student');
    expect(inScope(directory)).toBe(true);
    expect(inScope({ path: 'backend/pom.xml', directory: false, name: 'pom.xml' })).toBe(false);
    expect(inScope({ path: 'frontend/package.json', directory: false, name: 'package.json' })).toBe(false);
  });

  it('aceita as exceções documentadas: arquivos gerados, fontes e prefixo da marca', () => {
    const generated = `frontend/src/${pt('service')}s/api-${pt('type')}s.ts`;
    expect(guard.isSkippedFile(generated)).toBe(true);
    expect(guard.isSkippedFile('frontend/src/Sample.tsx')).toBe(false);
    const brandPath = `frontend/public/${pt('brand')}/fb-logo.png`;
    const brand = guard.scanName({ path: brandPath, directory: false, name: 'fb-logo.png' });
    expect(brand).toEqual([]);
  });

  it('exclusões temporárias: arquivo, arquivo novo e exclusão morta', () => {
    const student = pt('student');
    const data = JSON.parse(
      JSON.stringify({ words: { [student]: 'student' }, exceptions: [] }),
    ) as GlossaryData;
    const withExclusions = new LanguageGuard(data, {
      files: [{ path: 'frontend/src/Old.tsx' }, { path: 'frontend/src/Gone.tsx' }],
    });
    const old: Violation = {
      path: 'frontend/src/Old.tsx',
      line: 1,
      kind: 'identifier',
      text: 'x',
      term: student,
      english: 'student',
      hint: '',
    };
    const fresh: Violation = { ...old, path: 'frontend/src/New.tsx' };
    const { remaining, dead } = withExclusions.applyExclusions([old, fresh], () => true);
    expect(remaining).toEqual([fresh]);
    expect(dead).toEqual(['frontend/src/Gone.tsx']);
  });

  it('recusa exceção sem motivo e sem categoria do Princípio VI; aponta exceção morta', () => {
    const base = {
      words: {},
      exceptions: [{ id: 'x', mode: 'term', constitutionCategory: 'c' }],
    } as GlossaryData;
    expect(() => new LanguageGuard(base)).toThrow(/sem motivo/);
    const noCategory = { words: {}, exceptions: [{ id: 'x', mode: 'term', reason: 'r' }] } as GlossaryData;
    expect(() => new LanguageGuard(noCategory)).toThrow(/sem categoria/);
    const fresh = LanguageGuard.load(root);
    expect(fresh.deadExceptions('typescript')).not.toEqual([]);
  });
});
