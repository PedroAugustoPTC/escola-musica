#!/usr/bin/env node
// Gera a lista de retenção da fatia 2 (T026): o que fica em português no `core` por decisão de fronteira (R-01).
//
//   node gen-slice-2.mjs [--root <raiz>]   →   tools/held-back/slice-2.json
//
// B1: constantes de enumeração (ligadas a `valueOf`/`.name()` e ao banco).
// B2: componentes de `record` que o Jackson lê ou grava (esquemas de IA, jsonb, arquivo de carga, entrada do LLM).
// B3: literais de uma palavra que são chave de JSON, detalhe de erro, valor persistido ou entrada de teste.
// permanent: exceções permanentes (`download-file-name`, `teacher-keywords`), nunca migradas.
//
// A lista é só dados: a receita de renomeação a lê, o `rename-check` confere que o retido permaneceu igual, e o
// guardião a recebe como exclusão com dono = fatia 4 (T044).
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import { lex } from '../lib/java-lexer.mjs';
import { scanTypes } from '../lib/java-types.mjs';

const args = process.argv.slice(2);
const root = resolve(args.includes('--root') ? args[args.indexOf('--root') + 1] : process.cwd());
const coreRoot = join(root, 'backend/nucleo/src');
const map = JSON.parse(readFileSync(join(root, 'specs/003-migrate-code-to-english/tools/maps/slice-2.json'), 'utf8'));
const mapKeys = new Set(map.map((item) => item.antigo));

/** Raízes do que o Jackson (de)serializa; o fecho transitivo por tipos de componente entra em B2. */
const JACKSON_ROOTS = [
  'SaidaRelatorioAulaIa', 'SaidaRelatorioSemestralIa', // resposta do LLM (esquema JSON de IA)
  'ConteudoRelatorioAula', 'ConteudoRelatorioSemestral', 'InformacoesGerais', // colunas jsonb
  'ArquivoCarga', // arquivo JSON da carga inicial
  'EntradaTarefa', 'EntradaConsolidacaoSemestral', 'EntradaRevisaoSemestral', // entrada enviada ao LLM
];
/** Literais que nunca migram (exceções permanentes de `.specify/memory/glossary.json`). */
const PERMANENT = [
  { prefix: 'relatorio-aula-', exception: 'download-file-name' },
  { prefix: 'relatorio-semestral-', exception: 'download-file-name' },
  { literal: 'sair', exception: 'teacher-keywords' },
];

function* walk(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const full = join(directory, entry.name);
    if (entry.isDirectory()) yield* walk(full);
    else if (full.endsWith('.java')) yield full;
  }
}

const types = []; // { path, kind, file, components?, constants? }
for (const file of walk(coreRoot)) {
  const { tokens } = lex(readFileSync(file, 'utf8'));
  for (const type of scanTypes(tokens)) {
    types.push({
      path: type.path,
      kind: type.kind,
      file: relative(root, file).split(sep).join('/'),
      components: type.components?.map((component) => ({ name: component.name, types: component.types })),
      constants: type.constants?.map((constant) => constant.name),
    });
  }
}

const bySimpleName = new Map();
for (const type of types) {
  const simple = type.path.split('.').at(-1);
  if (!bySimpleName.has(simple)) bySimpleName.set(simple, []);
  bySimpleName.get(simple).push(type);
}

// B1: toda constante de enumeração do core
const B1 = {};
for (const type of types.filter((candidate) => candidate.kind === 'enum').sort((a, b) => a.path.localeCompare(b.path))) {
  B1[type.path] = { file: type.file, members: type.constants };
}

// B2: fecho transitivo dos tipos serializados pelo Jackson (somente `record`; os componentes são as chaves JSON)
const B2 = {};
const visit = (simple, via) => {
  for (const type of bySimpleName.get(simple) ?? []) {
    if (B2[type.path] || type.kind !== 'record') continue;
    B2[type.path] = { file: type.file, members: type.components.map((component) => component.name), via };
    for (const component of type.components) for (const name of component.types) visit(name, type.path);
  }
};
for (const rootName of JACKSON_ROOTS) {
  visit(rootName, 'raiz');
  // interface selada: as permitidas (`EntradaTarefa.EstruturacaoAula`, …) são serializadas como o próprio tipo
  for (const type of types) if (type.path.startsWith(`${rootName}.`) && type.kind === 'record') visit(type.path.split('.').at(-1), rootName);
}

// B3 e permanentes: literais de uma palavra do core (produção e teste) que casam com o vocabulário do mapa
const B3 = {};
const permanent = [];
for (const file of walk(coreRoot)) {
  const relativePath = relative(root, file).split(sep).join('/');
  const { tokens } = lex(readFileSync(file, 'utf8'));
  for (const token of tokens.filter((candidate) => candidate.type === 'string')) {
    const value = token.value;
    const rule = PERMANENT.find((candidate) => (candidate.prefix ? value.startsWith(candidate.prefix) : value === candidate.literal));
    if (rule) {
      permanent.push({ file: relativePath, line: token.line, literal: value, exception: rule.exception });
      continue;
    }
    if (value.startsWith('br.com.felipeblaft')) continue; // pacote em string: migra à mão em T039/T041, não é retenção
    const pieces = value.split(/[.-]/).filter(Boolean);
    if (!/^[A-Za-z_][\w.-]*$/.test(value) || !(mapKeys.has(value) || pieces.some((piece) => mapKeys.has(piece)))) continue;
    (B3[value] ??= []).push(`${relativePath}:${token.line}`);
  }
}

const result = {
  gerado_por: 'tools/held-back/gen-slice-2.mjs (T026)',
  regra: 'Retido = permanece IGUAL nesta fatia. B1/B2: identificadores (a receita não os renomeia). B3: literais (a receita nunca altera literais). Dono das exclusões no guardião: fatia 4 (T044).',
  B1, B2, B3, permanent,
};
writeFileSync(join(root, 'specs/003-migrate-code-to-english/tools/held-back/slice-2.json'), JSON.stringify(result, null, 2) + '\n');
const count = (object) => Object.values(object).reduce((sum, value) => sum + (value.members?.length ?? value.length), 0);
console.log(`B1: ${Object.keys(B1).length} enums, ${count(B1)} constantes`);
console.log(`B2: ${Object.keys(B2).length} records, ${count(B2)} componentes`);
console.log(`B3: ${Object.keys(B3).length} literais distintos, ${count(B3)} ocorrências`);
console.log(`permanentes: ${permanent.length} ocorrências`);
console.log('B2 tipos:', Object.keys(B2).join(', '));
