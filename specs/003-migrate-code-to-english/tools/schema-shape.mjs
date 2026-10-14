// uso: node schema-shape.mjs <árvore-base>  (rename-check --contracts da fatia 3, T098)
// Compara a forma dos esquemas de IA: a base com os nomes trocados pelo mapa deve ser igual ao esquema novo.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const sp = process.argv[2];
const idx = JSON.parse(readFileSync(`${root}specs/003-migrate-code-to-english/tools/maps/slice-3.json`, 'utf8'));
const map = new Map(idx.map((x) => [x.antigo, x.novo]));
const rename = (name) => map.get(name) ?? name;

function mapValue(value, parentKey) {
  if (Array.isArray(value)) return value.map((item) => (typeof item === 'string' && parentKey === 'required' ? rename(item) : mapValue(item, parentKey)));
  if (value && typeof value === 'object') {
    const out = {};
    for (const [key, inner] of Object.entries(value)) {
      const structural = ['type', 'properties', 'required', 'items', 'additionalProperties', 'enum', 'description', 'maxItems', 'maxLength', 'minItems', 'minLength', 'title', '$schema', '$id', '$ref', '$defs', 'definitions', 'format', 'pattern', 'anyOf', 'oneOf', 'allOf', 'default', 'minimum', 'maximum', 'const'];
      const isPropertyName = parentKey === 'properties' || parentKey === '$defs' || parentKey === 'definitions';
      out[isPropertyName ? rename(key) : key] = mapValue(inner, structural.includes(key) || isPropertyName ? key : key);
    }
    return out;
  }
  if (typeof value === 'string' && parentKey === '$ref') return value.replace(/[^/]+$/, (last) => rename(last));
  return value;
}

const pairs = [
  ['backend/aplicacao/src/main/resources/ia/schemas/relatorio-aula.schema.json', 'backend/application/src/main/resources/ai/schemas/lesson-report.schema.json'],
  ['backend/aplicacao/src/main/resources/ia/schemas/relatorio-semestral.schema.json', 'backend/application/src/main/resources/ai/schemas/semester-report.schema.json'],
];
let bad = 0;
for (const [base, work] of pairs) {
  const baseJson = JSON.parse(readFileSync(`${sp}/${base}`, 'utf8')); const workJson = JSON.parse(readFileSync(root + work, 'utf8')); delete baseJson.$id; delete workJson.$id; const a = JSON.stringify(mapValue(baseJson, ''));
  const b = JSON.stringify(workJson);
  const same = a === b;
  if (!same) bad++;
  console.log(`${work}: ${same ? 'forma idêntica à da base (só nomes trocados pelo mapa; $id é o identificador do esquema e muda com o nome)' : 'DIFERENTE'}`);
  if (!same) {
    let i = 0;
    while (a[i] === b[i]) i++;
    console.log('  base :', a.slice(Math.max(0, i - 60), i + 80));
    console.log('  novo :', b.slice(Math.max(0, i - 60), i + 80));
  }
}
process.exit(bad ? 1 : 0);
