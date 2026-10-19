// Normaliza a exportação de workflows do n8n e compara a instância com o repositório (feature 004, R-18).
//
//   node normalize-workflow.mjs normalize <pasta-de-entrada> <pasta-de-saida>
//   node normalize-workflow.mjs compare <pasta-da-instancia> <pasta-de-referencia>
//
// `normalize` valida TODOS os arquivos antes de gravar qualquer um e não grava nada se achar credencial,
// token ou URL com senha. `compare` lista só nomes de workflow e caminhos de campo, nunca valores.
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const VOLATILE_FIELDS = [
  'createdAt',
  'updatedAt',
  'versionId',
  'activeVersionId',
  'shared',
  'tags',
  'active',
  'isArchived',
  'triggerCount',
  'pinData',
  'staticData',
  'versionCounter',
  'versionMetadata',
  'sourceWorkflowId',
  'parentFolderId',
];

// a exportação do n8n 2.x preenche estes campos vazios; no repositório eles não existem (spike da Fase 1)
const EMPTY_BY_DEFAULT = ['description', 'meta', 'nodeGroups'];

function isEmpty(value) {
  return (
    value === null ||
    value === undefined ||
    (Array.isArray(value) && value.length === 0) ||
    (typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === 0)
  );
}

const FORBIDDEN_KEYS = new Set(['credentials', 'credentialId']);

const SECRET_PATTERNS = [
  ['BOT_TOKEN', /(?<![0-9])[0-9]{6,}:[A-Za-z0-9_-]{30,}/],
  ['API_KEY', /\bgsk_[A-Za-z0-9]{10,}|\bsk-[A-Za-z0-9_-]{10,}/],
  ['BEARER', /Bearer\s+[A-Za-z0-9._~+/=-]{8,}/],
  ['URL_USERINFO', /:\/\/[^/\s:@"']+:[^/\s@"']+@/],
];

/** Conteúdo que nunca pode ir para o repositório: devolve só caminho e código, nunca o valor. */
export function findUnsafeContent(value, path = '$', found = []) {
  if (Array.isArray(value)) {
    value.forEach((item, index) => findUnsafeContent(item, `${path}[${index}]`, found));
  } else if (value && typeof value === 'object') {
    for (const [key, child] of Object.entries(value)) {
      if (FORBIDDEN_KEYS.has(key)) {
        found.push({ path: `${path}.${key}`, code: 'CREDENTIAL_KEY' });
      }
      findUnsafeContent(child, `${path}.${key}`, found);
    }
  } else if (typeof value === 'string') {
    for (const [code, pattern] of SECRET_PATTERNS) {
      if (pattern.test(value)) {
        found.push({ path, code });
      }
    }
  }
  return found;
}

function sortKeys(value) {
  if (Array.isArray(value)) {
    return value.map(sortKeys);
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((key) => [key, sortKeys(value[key])]),
    );
  }
  return value;
}

/** Remove os campos voláteis e ordena as chaves; não altera o objeto recebido. */
export function normalize(workflow) {
  const copy = JSON.parse(JSON.stringify(workflow));
  for (const field of VOLATILE_FIELDS) {
    delete copy[field];
  }
  if (copy.meta && typeof copy.meta === 'object') {
    delete copy.meta.instanceId;
  }
  for (const field of EMPTY_BY_DEFAULT) {
    if (isEmpty(copy[field])) {
      delete copy[field];
    }
  }
  return sortKeys(copy);
}

export function serialize(workflow) {
  return `${JSON.stringify(workflow, null, 2)}\n`;
}

/** Caminhos em que dois valores já normalizados divergem. */
export function differences(left, right, path = '$', found = []) {
  if (Array.isArray(left) && Array.isArray(right)) {
    const size = Math.max(left.length, right.length);
    for (let index = 0; index < size; index += 1) {
      differences(left[index], right[index], `${path}[${index}]`, found);
    }
  } else if (left && right && typeof left === 'object' && typeof right === 'object' && !Array.isArray(left) && !Array.isArray(right)) {
    for (const key of new Set([...Object.keys(left), ...Object.keys(right)])) {
      differences(left[key], right[key], `${path}.${key}`, found);
    }
  } else if (JSON.stringify(left) !== JSON.stringify(right)) {
    found.push(path);
  }
  return found;
}

function readDirectory(directory) {
  const workflows = new Map();
  for (const name of readdirSync(directory).filter((file) => file.endsWith('.json')).sort()) {
    workflows.set(name.slice(0, -'.json'.length), JSON.parse(readFileSync(join(directory, name), 'utf8')));
  }
  return workflows;
}

function runNormalize(inputDirectory, outputDirectory) {
  const workflows = readDirectory(inputDirectory);
  const unsafe = [];
  for (const [name, workflow] of workflows) {
    for (const finding of findUnsafeContent(workflow)) {
      unsafe.push(`[UNSAFE_CONTENT] ${name}.json ${finding.path} (${finding.code})`);
    }
  }
  if (unsafe.length > 0) {
    console.error(unsafe.join('\n'));
    console.error('Nada foi gravado: remova a credencial, o token ou a URL com senha do workflow.');
    return 1;
  }
  mkdirSync(outputDirectory, { recursive: true });
  for (const [name, workflow] of workflows) {
    writeFileSync(join(outputDirectory, `${name}.json`), serialize(normalize(workflow)));
  }
  console.log(`${workflows.size} workflow(s) normalizado(s).`);
  return 0;
}

function runCompare(actualDirectory, referenceDirectory) {
  const actual = readDirectory(actualDirectory);
  const reference = existsSync(referenceDirectory) ? readDirectory(referenceDirectory) : new Map();
  const problems = [];
  for (const [name, workflow] of actual) {
    if (!reference.has(name)) {
      problems.push(`✘ workflow '${name}' existe só na instância [EXTRA_IN_INSTANCE]`);
      continue;
    }
    for (const path of differences(normalize(workflow), normalize(reference.get(name)))) {
      problems.push(`✘ workflow '${name}' diverge do repositório em ${path} [DIFFERENT_FROM_REPOSITORY]`);
    }
  }
  for (const name of reference.keys()) {
    if (!actual.has(name)) {
      problems.push(`✘ workflow '${name}' está no repositório e falta na instância [MISSING_IN_INSTANCE]`);
    }
  }
  if (problems.length > 0) {
    console.log(problems.join('\n'));
    return 1;
  }
  console.log(`✔ ${actual.size} workflow(s) da instância iguais aos do repositório.`);
  return 0;
}

function main(argv) {
  const [command, first, second] = argv;
  if (command === 'normalize' && first && second) {
    return runNormalize(first, second);
  }
  if (command === 'compare' && first && second) {
    return runCompare(first, second);
  }
  console.error('Uso: normalize-workflow.mjs normalize <entrada> <saida> | compare <instancia> <referencia>');
  return 2;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = main(process.argv.slice(2));
}
