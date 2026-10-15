#!/usr/bin/env node
// Gera `V7__rename_to_english.sql` e o mapa de renomeação do banco (`db/rename-map.json`) a partir do catálogo da V6
// (`tools/db-catalog-v6.json`), do `data-model.md` e do mapa de identificadores da fatia 3 (T058, T081, T082).
//
//   node gen-v7.mjs [--root <raiz>] [--out <pasta-das-migrações>] [--map-out <rename-map.json>]
//
// Ordem da migração (research.md R-03): tabelas, colunas, sequência, restrições (inclusive NOT NULL, chaves primárias
// e estrangeiras de nome automático) e índices avulsos, UM POR UM e explícitos; chaves dos documentos `jsonb`
// (função auxiliar temporária em `pg_temp`, só chaves, nunca valores); índice por expressão JSON recriado.
// Nada de laço genérico: cada nome vem de um par fechado, conferido contra o catálogo (nome desconhecido aborta).
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { loadDbModel } from './lib/db-model.mjs';
import { loadRenameMap } from './lib/rename-map.mjs';

const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const root = resolve(option('--root', process.cwd()));
const TOOLS = join(root, 'specs/003-migrate-code-to-english/tools');
const OUT = resolve(root, option('--out', 'backend/application/src/main/resources/db/migration'));
const MAP_OUT = resolve(root, option('--map-out', 'backend/application/src/test/resources/db/rename-map.json'));

const catalog = JSON.parse(readFileSync(join(TOOLS, 'db-catalog-v6.json'), 'utf8'));
const model = loadDbModel(root);
const map = loadRenameMap(root, 3);

// ---- tabelas e colunas ---------------------------------------------------------------------------------------------
const tableNew = (table) => model.tables.get(table) ?? table;
const columnNew = (table, column) => model.column(table, column);
const problems = [];

const tables = catalog.tables.map((table) => table.name).sort();
for (const table of tables) if (!model.tables.has(table)) problems.push(`tabela sem par no data-model: ${table}`);
for (const [table] of model.tables) if (!tables.includes(table)) problems.push(`data-model cita tabela inexistente na V6: ${table}`);
for (const table of catalog.tables) {
  const names = new Set(table.columns.map((column) => column.name));
  for (const [column] of model.columns.get(table.name) ?? []) if (!names.has(column)) problems.push(`data-model cita coluna inexistente: ${table.name}.${column}`);
}

// ---- restrições, índices e sequência ---------------------------------------------------------------------------------
const columnsOfDefinition = (definition) => {
  const match = definition.match(/\(([^)]*)\)/);
  return match ? match[1].split(',').map((part) => part.trim().replace(/^"|"$/g, '')) : [];
};

/** Nome automático do PostgreSQL para uma restrição (a partir da tabela e das colunas dadas). */
function autoName(type, table, definition, lookupColumn) {
  if (type === 'p') return `${table}_pkey`;
  if (type === 'n') return `${table}_${lookupColumn(definition.replace(/^NOT NULL /, '').trim())}_not_null`;
  if (type === 'f') return `${table}_${columnsOfDefinition(definition).map(lookupColumn).join('_')}_fkey`;
  if (type === 'u') return `${table}_${columnsOfDefinition(definition).map(lookupColumn).join('_')}_key`;
  return null;
}

const constraintRenames = []; // { table (nova), old, novo, kind }
const backingIndexes = new Set();
for (const constraint of catalog.constraints) {
  const oldTable = constraint.table;
  const newTable = tableNew(oldTable);
  const automatic = autoName(constraint.type, oldTable, constraint.definition, (column) => column) === constraint.name;
  let novo;
  if (automatic) {
    novo = autoName(constraint.type, newTable, constraint.definition, (column) => columnNew(oldTable, column));
  } else {
    novo = map.ident.get(constraint.name);
    if (!novo) problems.push(`restrição de nome explícito sem par no mapa: ${oldTable}.${constraint.name}`);
  }
  if (constraint.type === 'p' || constraint.type === 'u') backingIndexes.add(constraint.name);
  if (novo && novo !== constraint.name) constraintRenames.push({ table: newTable, old: constraint.name, novo, kind: constraint.type });
}
const indexRenames = [];
for (const index of catalog.indexes) {
  if (backingIndexes.has(index.name)) continue; // acompanha a restrição que o sustenta
  const novo = map.ident.get(index.name);
  if (!novo) problems.push(`índice avulso sem par no mapa: ${index.table}.${index.name}`);
  else if (novo !== index.name) indexRenames.push({ old: index.name, novo });
}
const sequenceRenames = catalog.sequences.map((sequence) => {
  const [table, column] = sequence.ownedBy.split('.');
  return { old: sequence.name, novo: `${tableNew(table)}_${columnNew(table, column)}_seq` };
});

// nomes novos distintos entre si
const seen = new Map();
for (const { novo, old } of [...constraintRenames, ...indexRenames, ...sequenceRenames]) {
  if (seen.has(novo) && seen.get(novo) !== old) problems.push(`nome novo repetido: ${novo} (${seen.get(novo)} e ${old})`);
  seen.set(novo, old);
}

// ---- chaves JSON dos documentos gravados (identifier-map.md §3; só chaves, nunca valores) -----------------------------
const key = (name) => {
  const novo = map.ident.get(name);
  if (!novo) problems.push(`chave JSON sem par no mapa: ${name}`);
  return novo ?? name;
};
const pairs = (names) => Object.fromEntries(names.map((name) => [name, key(name)]));
const LESSON_CONTENT = ['conteudosTrabalhados', 'evolucao', 'dificuldades', 'atividadesPropostas', 'observacoes'];
const SEMESTER_CONTENT = [
  'frequenciaEEstudo', 'frequenciaAulas', 'regularidadeEstudoEmCasa', 'engajamento', 'comentarioGeral',
  'tecnica', 'conteudosTrabalhados', 'evolucaoTecnica', 'pontosADesenvolver', 'estudosRealizados',
  'musicalidade', 'repertorioEstudado', 'som', 'ritmo', 'fraseado', 'articulacao', 'estilo', 'comentarioMusical',
  'leituraEMemorizacao', 'avaliacao', 'comentario',
  'pontosDeAtencao', 'estrategiasPedagogicas', 'itens',
  'acompanhamentoFamiliar', 'nivelDePresenca', 'observacaoDoProfessor',
  'planejamentoProximoSemestre', 'metasTecnicas', 'metasMusicais', 'habitosDeEstudo', 'repertorioProposto', 'estudosTecnicos', 'leituraETeoria', 'projetoDoSemestre',
  'parecerFinal',
];
const GENERAL_INFORMATION = ['nome', 'idade', 'instrumento', 'professor', 'periodoChave', 'periodoInicio', 'periodoFim', 'quantidadeAulasConsideradas'];
const JOB_PARAMETERS = ['texto', 'versaoBase', 'textoEntendido', 'arquivoCanal', 'arquivoCanalUnico', 'arquivoMime', 'arquivoTamanho', 'mensagemEtapa', 'periodoChave', 'confirmarMesmoComEmAberto'];
const FLOW_DATA = ['confirmacao', 'opcoes', 'histAluno', 'histItens', 'alunoEmAberto', 'semAluno', 'semOpcoes', 'semPeriodo'];
const jsonRenames = [
  { table: 'lesson_report', column: 'content', keys: pairs(LESSON_CONTENT), note: 'conteúdo do relatório de aula (5 chaves)' },
  { table: 'semester_report', column: 'content', keys: pairs(SEMESTER_CONTENT), note: 'conteúdo do relatório semestral (seções e subchaves)' },
  { table: 'semester_report', column: 'general_information', keys: pairs(GENERAL_INFORMATION), note: 'informações gerais do semestral' },
  // `report_version.content` guarda aula ou semestral conforme `report_type` (valores ainda em português na V7)
  { table: 'report_version', column: 'content', keys: pairs(LESSON_CONTENT), where: "report_type = 'AULA'", note: 'versão de relatório de aula' },
  { table: 'report_version', column: 'content', keys: pairs(SEMESTER_CONTENT), where: "report_type = 'SEMESTRAL'", note: 'versão de relatório semestral' },
  { table: 'processing_job', column: 'parameters', keys: pairs(JOB_PARAMETERS), note: 'parâmetros do trabalho' },
  { table: 'telegram_link', column: 'flow_data', keys: pairs(FLOW_DATA), note: 'dados do fluxo da conversa do Telegram' },
];

// índice por expressão JSON que usa a chave `periodoChave` (R-03 passo 6)
const expressionIndex = catalog.indexes.find((index) => /parametros\s*->>\s*'periodoChave'/.test(index.definition));
if (!expressionIndex) problems.push("índice por expressão JSON (parametros ->> 'periodoChave') não encontrado no catálogo");

if (problems.length) {
  console.error(`${problems.length} problema(s):\n${problems.map((problem) => `  - ${problem}`).join('\n')}`);
  process.exit(1);
}

// ---- SQL ---------------------------------------------------------------------------------------------------------------
const sql = [];
const comment = (text) => sql.push('', `-- ${text}`);
sql.push(
  '-- V7 (feature 003): renomeia tabelas, colunas, sequência, restrições, índices e chaves JSON do esquema para inglês.',
  '-- Só nomes: tipos, tamanhos, nulabilidade, padrões, restrições e índices continuam idênticos (FR-014). Os valores de',
  '-- enumeração (status, tipos, etapas…) continuam em português até a V8 (são também o fio). Nenhuma migração anterior',
  '-- é editada (FR-013). Gerada por specs/003-migrate-code-to-english/tools/gen-v7.mjs a partir de db-catalog-v6.json.',
);
comment('1. Tabelas (colunas, chaves, índices e dados acompanham pelo OID)');
for (const [antigo, novo] of [...model.tables].sort(([a], [b]) => a.localeCompare(b))) sql.push(`ALTER TABLE ${antigo} RENAME TO ${novo};`);
comment('2. Colunas');
for (const [antigo, novoTable] of [...model.tables].sort(([a], [b]) => a.localeCompare(b))) {
  for (const [coluna, novo] of model.columns.get(antigo) ?? []) if (coluna !== novo) sql.push(`ALTER TABLE ${novoTable} RENAME COLUMN ${coluna} TO ${novo};`);
}
comment('3. Sequência da identidade de channel_action.seq');
for (const { old, novo } of sequenceRenames) sql.push(`ALTER SEQUENCE ${old} RENAME TO ${novo};`);
comment('4. Restrições (NOT NULL, chave primária, chave estrangeira, unicidade, CHECK); o índice que sustenta a restrição acompanha');
for (const { table, old, novo } of constraintRenames) sql.push(`ALTER TABLE ${table} RENAME CONSTRAINT ${old} TO ${novo};`);
comment('5. Índices avulsos (parciais e por expressão)');
for (const { old, novo } of indexRenames) sql.push(`ALTER INDEX ${old} RENAME TO ${novo};`);
comment('6. Chaves JSON só de banco: função auxiliar temporária (existe só nesta sessão; não é objeto do esquema).');
comment('   Percorre o jsonb recursivamente e renomeia APENAS chaves; valores (textos do professor ou da IA) nunca são alterados.');
sql.push(
  'CREATE FUNCTION pg_temp.rename_json_keys(document jsonb, names jsonb) RETURNS jsonb',
  'LANGUAGE sql IMMUTABLE AS $$',
  '  SELECT CASE jsonb_typeof(document)',
  "    WHEN 'object' THEN COALESCE((",
  '      SELECT jsonb_object_agg(COALESCE(names ->> entry.key, entry.key), pg_temp.rename_json_keys(entry.value, names))',
  '        FROM jsonb_each(document) AS entry), \'{}\'::jsonb)',
  "    WHEN 'array' THEN COALESCE((",
  '      SELECT jsonb_agg(pg_temp.rename_json_keys(element.value, names) ORDER BY element.position)',
  '        FROM jsonb_array_elements(document) WITH ORDINALITY AS element(value, position)), \'[]\'::jsonb)',
  '    ELSE document',
  '  END',
  '$$;',
);
for (const { table, column, keys, where, note } of jsonRenames) {
  sql.push('', `-- ${note}`, `UPDATE ${table}`, `   SET ${column} = pg_temp.rename_json_keys(${column}, '${JSON.stringify(keys)}'::jsonb)`, ` WHERE ${column} IS NOT NULL${where ? `\n   AND ${where}` : ''};`);
}
comment('7. Índice por expressão JSON: recriado com a chave nova, DEPOIS da etapa 6 (sem isso o índice único deixaria de proteger)');
const newIndexName = map.ident.get(expressionIndex.name) ?? expressionIndex.name;
sql.push(`DROP INDEX ${newIndexName};`, `${renderIndexDefinition(expressionIndex, newIndexName)};`);

/** Definição do índice por expressão com os nomes novos (predicado e colunas pelo data-model; literais intactos). */
function renderIndexDefinition(index, newName) {
  const oldTable = index.table;
  const newTable = tableNew(oldTable);
  let text = index.definition;
  text = text.replace(/INDEX \w+ ON public\.\w+/, `INDEX ${newName} ON ${newTable}`);
  // palavras fora de literais entre aspas simples: coluna da tabela, trocada pelo data-model
  text = text
    .split(/('(?:[^']|'')*')/)
    .map((part, position) => (position % 2 === 1 ? part.replace(/^'periodoChave'$/, `'${key('periodoChave')}'`) : part.replace(/\b[a-z_][a-z0-9_]*\b/g, (word) => columnNew(oldTable, word))))
    .join('');
  // o PostgreSQL mostra `col IN ('A','B')` como `= ANY ((ARRAY[…])::text[])` e, ao reler esta forma, a mostra de outro
  // jeito; voltar a `IN (…)` faz o índice recriado ter exatamente a mesma definição do original (FR-014)
  text = text.replace(/\((\w+)\)::text = ANY \(\(ARRAY\[([^\]]*)\]\)::text\[\]\)/g, (_, column, items) => `${column} IN (${items.replace(/::character varying/g, '')})`);
  return text;
}
sql.push('');

mkdirSync(OUT, { recursive: true });
writeFileSync(join(OUT, 'V7__rename_to_english.sql'), `${sql.join('\n')}\n`);

// ---- mapa de renomeação como recurso de teste (cobertura conferida pelo teste de migração) ------------------------------
const renameMap = {
  descricao: 'Mapa de renomeação do banco aplicado pela V7 (gerado por tools/gen-v7.mjs). Recurso dos testes de migração.',
  tables: Object.fromEntries([...model.tables].sort(([a], [b]) => a.localeCompare(b))),
  columns: Object.fromEntries([...model.tables].sort(([a], [b]) => a.localeCompare(b)).map(([antigo]) => [antigo, Object.fromEntries(catalog.tables.find((table) => table.name === antigo).columns.map((column) => [column.name, columnNew(antigo, column.name)]))])),
  constraints: Object.fromEntries(constraintRenames.map(({ old, novo }) => [old, novo])),
  indexes: Object.fromEntries(indexRenames.map(({ old, novo }) => [old, novo])),
  sequences: Object.fromEntries(sequenceRenames.map(({ old, novo }) => [old, novo])),
  jsonKeys: jsonRenames.map(({ table, column, keys, where }) => ({ table, column, where: where ?? null, keys })),
  expressionIndex: { old: expressionIndex.name, novo: newIndexName },
};
mkdirSync(dirname(MAP_OUT), { recursive: true });
writeFileSync(MAP_OUT, `${JSON.stringify(renameMap, null, 2)}\n`);
console.log(
  `V7: ${model.tables.size} tabelas, ${[...model.columns.values()].reduce((sum, columns) => sum + columns.size, 0)} colunas, ${sequenceRenames.length} sequência, ${constraintRenames.length} restrições, ${indexRenames.length} índices avulsos, ${jsonRenames.length} conjuntos de chaves JSON`,
);
