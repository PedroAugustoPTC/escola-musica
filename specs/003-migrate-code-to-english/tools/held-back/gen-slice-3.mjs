#!/usr/bin/env node
// Gera a lista de retenção da fatia 3 (T052): o que fica em português na `aplicacao` por decisão de fronteira (R-01).
//
//   node gen-slice-3.mjs [--root <raiz>] [--dir backend/aplicacao]   →   tools/held-back/slice-3.json
//
// B1: constantes de enumeração ligadas a valor de configuração externo e a asserção de texto de teste
//     (`EntregaProativa`: `entrega-proativa=DIRETA|ORQUESTRADOR`). O resto das enumerações da aplicação migra.
// B2: componentes de `record` que são o contrato de fio do portal e do canal interno (DTOs de requisição e resposta,
//     nomes de campo JSON). A fatia 4 renomeia o contrato (OpenAPI, JSON Schema) e quita a retenção.
// B2 (parâmetros): parâmetros de controlador cujo nome é o nome da variável de caminho ou do parâmetro de consulta
//     (`@PathVariable UUID alunoId` sem nome explícito): trocar o nome trocaria a rota. Entram como membros do
//     controlador, junto com os componentes dos DTOs.
// B3: literais de uma palavra do vocabulário do mapa que permanecem (chave JSON de teste, valor persistido).
//     Quem migra literais (consultas, nomes de coluna, chaves de configuração) o faz por edição ferramentada
//     (`post-edits`); o que sobra nesta lista é o que ficou igual. Gere de novo no fim da fatia.
// permanent: exceções permanentes (`download-file-name`, `teacher-keywords`), nunca migradas.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import { lex } from '../lib/java-lexer.mjs';
import { scanTypes } from '../lib/java-types.mjs';

const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const root = resolve(option('--root', process.cwd()));
const moduleDir = option('--dir', 'backend/aplicacao');
const srcRoot = join(root, moduleDir, 'src');
const map = JSON.parse(readFileSync(join(root, 'specs/003-migrate-code-to-english/tools/maps/slice-3.json'), 'utf8'));
const mapKeys = new Set(map.map((item) => item.antigo));

/** Enumerações retidas (B1): valor de configuração externo, asserido por texto em teste. */
const B1_TYPES = ['PropriedadesAplicacao.EntregaProativa'];
/** Tipos de fio sem sufixo `Dto`/`Requisicao`. */
const WIRE_NAMES = new Set(['ErroResposta', 'AtualizacaoResposta', 'ConfirmacaoResposta', 'ErroCanalResposta', 'EstadoAcaoResposta']);
const isWire = (type) => {
  const simple = type.path.split('.').at(-1);
  return /(Dto|Requisicao)$/.test(simple) || WIRE_NAMES.has(simple);
};
/** Interfaces de fio: os métodos abstratos são os acessores dos componentes dos DTOs que as implementam (retidos). */
const WIRE_INTERFACES = { AcaoDto: ['acaoId', 'tipo'] };
/** Literais que nunca migram (exceções permanentes de `.specify/memory/glossary.json`). */
const PERMANENT = [
  { prefix: 'relatorio-aula-', exception: 'download-file-name' },
  { prefix: 'relatorio-semestral-', exception: 'download-file-name' },
  { literal: 'sair', exception: 'teacher-keywords' },
];
/** Anotações de binding de Spring MVC que, sem argumento de nome, usam o nome do parâmetro Java como nome na rota. */
const BINDING = /^@(PathVariable|RequestParam|RequestHeader|CookieValue)$/;

function* walk(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.name === 'target') continue;
    const full = join(directory, entry.name);
    if (entry.isDirectory()) yield* walk(full);
    else if (full.endsWith('.java')) yield full;
  }
}
const rel = (file) => relative(root, file).split(sep).join('/');

/** Nomes dos parâmetros ligados por nome implícito (anotação sem `("nome")`, `name =` ou `value =`). */
function implicitBindingParameters(tokens) {
  const names = [];
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];
    if (token.type !== 'annotation' || !BINDING.test(token.value)) continue;
    let j = i + 1;
    // argumentos da anotação
    let explicitName = false;
    if (tokens[j]?.value === '(') {
      let depth = 0;
      for (; j < tokens.length; j++) {
        if (tokens[j].value === '(') depth++;
        else if (tokens[j].value === ')' && --depth === 0) break;
        else if (depth === 1 && (tokens[j].type === 'string' || ((tokens[j].value === 'name' || tokens[j].value === 'value') && tokens[j + 1]?.value === '='))) explicitName = true;
      }
      j++;
    }
    if (explicitName) continue;
    // próximas anotações e o tipo; o nome é o último identificador antes de `,` ou `)`
    let last = null;
    let depth = 0;
    for (; j < tokens.length; j++) {
      const value = tokens[j].value;
      if (tokens[j].type === 'op' && (value === '<' || value === '(')) depth++;
      else if (tokens[j].type === 'op' && (value === '>' || value === '>>')) depth -= value.length;
      else if (tokens[j].type === 'op' && value === ')' && depth > 0) depth--;
      else if (depth === 0 && tokens[j].type === 'op' && (value === ',' || value === ')')) break;
      else if (tokens[j].type === 'ident' && depth === 0) last = value;
    }
    if (last) names.push(last);
  }
  return names;
}

const B1 = {};
const B2 = {};
const permanent = [];
const B3 = {};

for (const file of walk(srcRoot)) {
  const source = readFileSync(file, 'utf8');
  const { tokens } = lex(source);
  const path = rel(file);
  const isMain = path.includes('/src/main/');

  for (const type of scanTypes(tokens)) {
    if (type.kind === 'enum' && B1_TYPES.includes(type.path)) B1[type.path] = { file: path, members: type.constants.map((constant) => constant.name) };
    if (isMain && type.kind === 'interface' && WIRE_INTERFACES[type.path]) {
      B2[type.path] = { file: path, members: WIRE_INTERFACES[type.path], via: 'interface-de-fio' };
    }
    if (isMain && type.kind === 'record' && isWire(type)) {
      B2[type.path] = { file: path, members: type.components.map((component) => component.name), via: 'dto-de-fio' };
    }
  }

  // controladores: parâmetros com nome implícito entram como membros retidos do controlador
  if (isMain && /Controlador\.java$/.test(path)) {
    const names = [...new Set(implicitBindingParameters(tokens))];
    if (names.length) {
      const controller = scanTypes(tokens).find((type) => type.path.split('.').length === 1)?.path;
      if (controller) B2[controller] = { file: path, members: names.sort(), via: 'parametro-de-rota-implicito' };
    }
  }

  for (const token of tokens.filter((candidate) => candidate.type === 'string')) {
    const value = token.value;
    const rule = PERMANENT.find((candidate) => (candidate.prefix ? value.startsWith(candidate.prefix) : value === candidate.literal));
    if (rule) {
      permanent.push({ file: path, line: token.line, literal: value, exception: rule.exception });
      continue;
    }
    if (value.startsWith('br.com.felipeblaft')) continue;
    const pieces = value.split(/[.-]/).filter(Boolean);
    if (!/^[A-Za-z_][\w.-]*$/.test(value) || !(mapKeys.has(value) || pieces.some((piece) => mapKeys.has(piece)))) continue;
    (B3[value] ??= []).push(`${path}:${token.line}`);
  }
}

const sortKeys = (object) => Object.fromEntries(Object.entries(object).sort(([a], [b]) => a.localeCompare(b)));
const result = {
  gerado_por: 'tools/held-back/gen-slice-3.mjs (T052)',
  regra:
    'Retido = permanece IGUAL nesta fatia. B1/B2: identificadores (a receita não os renomeia). B3: literais (a receita nunca altera literais; o que foi migrado por edição ferramentada sai da lista ao gerar de novo). Dono das exclusões no guardião: fatia 4 (T044).',
  B1: sortKeys(B1),
  B2: sortKeys(B2),
  B3: sortKeys(B3),
  permanent,
};
writeFileSync(join(root, 'specs/003-migrate-code-to-english/tools/held-back/slice-3.json'), `${JSON.stringify(result, null, 2)}\n`);
const count = (object) => Object.values(object).reduce((sum, value) => sum + (value.members?.length ?? value.length), 0);
console.log(`B1: ${Object.keys(B1).length} enums, ${count(B1)} constantes`);
console.log(`B2: ${Object.keys(B2).length} tipos, ${count(B2)} membros`);
console.log(`B3: ${Object.keys(B3).length} literais distintos, ${count(B3)} ocorrências`);
console.log(`permanentes: ${permanent.length} ocorrências`);
