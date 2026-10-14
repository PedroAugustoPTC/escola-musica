// Comparação de contratos (OpenAPI e JSON Schema) antigo × novo (T108): mesma forma, só nomes diferentes.
//
//   const result = compareContracts(velho, novo);
//   result.errors  → diferenças de forma (tipo, quantidade, ordem, número, booleano…)
//   result.names   → { kind: Map(antigo → Set(novo)) } com os nomes trocados, por tipo de nome
//
// Os dois documentos são percorridos em paralelo, na mesma ordem. Objetos devem ter o mesmo número de chaves e a chave
// de mesma posição pode mudar de nome; listas devem ter o mesmo tamanho; escalares só podem diferir quando são nomes
// (chave, `$ref`, `name`, `operationId`, `tags`, `required`, `enum`, `discriminator`) ou texto livre (`description`,
// `summary`, `title`, `example`, `externalDocs`…), que não entra na conferência de forma.
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

const FREE_TEXT = new Set(['$id', 'description', 'summary', 'title', 'example', 'examples', 'externalDocs', 'termsOfService', 'x-description', 'pattern']);
/** Chaves cujo valor é um mapa de nomes definidos (a chave é um nome do tipo indicado). */
const NAME_MAPS = {
  paths: 'path',
  properties: 'property',
  schemas: 'schema',
  parameters: 'parameter-def',
  responses: 'response-def',
  securitySchemes: 'security',
  $defs: 'schema',
  definitions: 'schema',
  headers: 'header',
  webhooks: 'webhook',
  security: 'security',
};
const UNNAMED_MAPS = new Set(['content', 'paths-item', 'mapping']);

export function loadContract(path, root = process.cwd()) {
  const text = readFileSync(path, 'utf8');
  if (path.endsWith('.json')) return JSON.parse(text);
  const require = createRequire(`${root}/frontend/package.json`);
  return require('js-yaml').load(text);
}

export function compareContracts(oldDoc, newDoc) {
  const errors = [];
  const names = {};
  const note = (kind, from, to) => {
    if (from === to) return;
    const map = (names[kind] ??= new Map());
    (map.get(from) ?? map.set(from, new Set()).get(from)).add(to);
  };
  const isObject = (value) => value && typeof value === 'object' && !Array.isArray(value);

  function walk(a, b, path, parentKey) {
    if (Array.isArray(a) || Array.isArray(b)) {
      if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) {
        errors.push(`${path}: lista de tamanho/forma diferente`);
        return;
      }
      a.forEach((item, index) => {
        if (typeof item === 'string' && typeof b[index] === 'string') {
          const kind = parentKey === 'required' ? 'property' : parentKey === 'enum' ? 'enum' : parentKey === 'tags' ? 'tag' : parentKey === 'security' ? 'security' : null;
          if (kind) return note(kind, item, b[index]);
          if (item !== b[index] && !FREE_TEXT.has(parentKey)) errors.push(`${path}[${index}]: texto diferente fora de lista de nomes`);
          return;
        }
        walk(item, b[index], `${path}[${index}]`, parentKey);
      });
      return;
    }
    if (isObject(a) || isObject(b)) {
      if (!isObject(a) || !isObject(b)) {
        errors.push(`${path}: tipo de nó diferente`);
        return;
      }
      const ka = Object.keys(a);
      const kb = Object.keys(b);
      if (ka.length !== kb.length) {
        errors.push(`${path}: ${ka.length} chaves × ${kb.length}`);
        return;
      }
      const named = NAME_MAPS[parentKey];
      ka.forEach((key, index) => {
        const other = kb[index];
        let kind = named;
        if (key !== other) {
          if (named) {
            if (named === 'path') {
              note('path', key, other);
              const segments = (value) => value.split('/');
              const sa = segments(key);
              const sb = segments(other);
              if (sa.length !== sb.length) errors.push(`${path}.${key}: segmentos de rota diferentes`);
              else sa.forEach((segment, i) => note(segment.startsWith('{') ? 'path-parameter' : 'path-segment', segment.replace(/[{}]/g, ''), sb[i].replace(/[{}]/g, '')));
            } else note(kind, key, other);
          } else if (parentKey === 'mapping') note('enum', key, other);
          else if (/^[0-9]{3}$/.test(key) || key.startsWith('x-') || UNNAMED_MAPS.has(parentKey) || key.includes('/')) errors.push(`${path}: chave estrutural diferente "${key}" × "${other}"`);
          else errors.push(`${path}: chave "${key}" × "${other}" fora de mapa de nomes`);
        }
        walk(a[key], b[other], `${path}.${key}`, key);
      });
      return;
    }
    // escalares
    if (typeof a !== typeof b) {
      errors.push(`${path}: tipo ${typeof a} × ${typeof b}`);
      return;
    }
    if (a === b) return;
    if (typeof a !== 'string') {
      errors.push(`${path}: valor ${a} × ${b}`);
      return;
    }
    if (FREE_TEXT.has(parentKey)) return;
    if (parentKey === '$ref') {
      const ra = a.split('/');
      const rb = b.split('/');
      if (ra.length !== rb.length) errors.push(`${path}: $ref de forma diferente`);
      else note(ra.at(-2) === 'parameters' ? 'parameter-def' : ra.at(-2) === 'responses' ? 'response-def' : 'schema', ra.at(-1), rb.at(-1));
      return;
    }
    if (parentKey === 'name' && path.includes('parameters')) return note('parameter', a, b);
    if (parentKey === 'name') return note('tag', a, b);
    if (parentKey === 'operationId') return note('operation', a, b);
    if (parentKey === 'propertyName') return note('property', a, b);
    if (parentKey === 'const') return note('enum', a, b);
    if (parentKey === 'default') return note('enum', a, b);
    errors.push(`${path}: texto "${a}" × "${b}" não é nome nem texto livre`);
  }

  walk(oldDoc, newDoc, '$', '');
  return { errors, names };
}

/** Converte `names` em objeto simples { kind: { antigo: novo } } e lista os conflitos (um antigo com dois novos). */
export function flattenNames(names) {
  const flat = {};
  const conflicts = [];
  for (const [kind, map] of Object.entries(names)) {
    flat[kind] = {};
    for (const [from, tos] of map) {
      if (tos.size > 1) conflicts.push(`${kind}: "${from}" → ${[...tos].map((t) => `"${t}"`).join(' | ')}`);
      flat[kind][from] = [...tos][0];
    }
  }
  return { flat, conflicts };
}
