import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { semesterSections } from '../../src/services/semester';

/**
 * O conteúdo do relatório semestral chega do backend como JSON cujas chaves são contrato (o esquema de saída da IA). A tela
 * lê cada chave por texto, e o compilador não vê esse acesso: uma chave trocada de nome só aparece como campo vazio. Este
 * teste gera um conteúdo com TODAS as chaves do esquema do backend, cada uma com um valor próprio, e exige que cada valor
 * apareça em alguma seção.
 */
const SCHEMA_PATH = '../backend/application/src/main/resources/ai/schemas/semester-report.schema.json';

interface SchemaNode {
  type?: string;
  $ref?: string;
  properties?: Record<string, SchemaNode>;
}

function build(schema: SchemaNode, defs: Record<string, SchemaNode>, path: string, expected: string[]): unknown {
  const resolved = (schema.$ref ? defs[schema.$ref.replace('#/$defs/', '')] : schema) ?? schema;
  if (resolved.type === 'object' && resolved.properties) {
    return Object.fromEntries(
      Object.entries(resolved.properties)
        .filter(([key]) => !['noContentChange', 'actionRequest'].includes(key))
        .map(([key, child]) => [key, build(child, defs, `${path}.${key}`, expected)]),
    );
  }
  const value = `value:${path}`;
  expected.push(value);
  return resolved.type === 'array' ? [value] : value;
}

describe('seções do semestral × esquema do backend', () => {
  it('mostra o valor de toda chave do esquema (nenhuma chave lida com nome errado)', () => {
    const schema = JSON.parse(readFileSync(SCHEMA_PATH, 'utf8')) as SchemaNode & { $defs: Record<string, SchemaNode> };
    const expected: string[] = [];
    const content = build(schema, schema.$defs, 'content', expected) as Record<string, unknown>;
    const info = {
      name: 'Aluno',
      age: 10,
      instrument: 'Piano',
      teacher: 'Professor',
      periodLabel: '1º semestre',
      periodStart: '2026-01-01',
      periodEnd: '2026-06-30',
      consideredLessonCount: 3,
    };

    const sections = semesterSections(info as never, content as never);
    const shown = sections.flatMap((section) => section.fields.flatMap((field) => [field.text, ...field.items]));

    const missing = expected.filter((value) => !shown.includes(value));
    expect(missing).toEqual([]);
  });
});
