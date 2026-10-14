#!/usr/bin/env node
// Gera `V8__rename_enum_values.sql` e o mapa de valores usado pelos testes (`db/v8-values.json`) — T121 a T125.
//
//   node gen-v8.mjs [--root <raiz>]
//
// Entradas: o catálogo do banco depois da V7 (`tools/db-catalog-v7.json`), os pares de constantes de enumeração da
// fatia 4 (`tools/enum-pairs-slice-4.json`, derivados do diff das enumerações), o mapa do contrato (`api-field-map.json`)
// e o vocabulário de ações de botão (`explicit-pairs.json` → `slice4.callbackActions`).
//
// Ordem (research.md R-03): descartar os objetos cujo predicado tem literal de enumeração; trocar os valores coluna a
// coluna; trocar as chaves e os valores do JSON do fio (`payload`, `compensation`) e os tokens de confirmação de
// `flow_data`; recriar os objetos com os literais novos (mesmos nomes); rede de segurança que ABORTA se sobrar valor
// antigo em coluna de conjunto fechado e confere se cada valor novo cabe no `varchar(n)`.
import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const root = resolve(option('--root', process.cwd()));
const TOOLS = join(root, 'specs/003-migrate-code-to-english/tools');
const catalog = JSON.parse(readFileSync(join(TOOLS, 'db-catalog-v7.json'), 'utf8'));
const enums = JSON.parse(readFileSync(join(TOOLS, 'enum-pairs-slice-4.json'), 'utf8'));
const api = JSON.parse(readFileSync(join(TOOLS, 'api-field-map.json'), 'utf8'));
const explicit = JSON.parse(readFileSync(join(TOOLS, 'explicit-pairs.json'), 'utf8'));
const actions = explicit.slice4.callbackActions;

const E = (name) => {
  if (!enums[name]) throw new Error(`enumeração sem pares: ${name}`);
  return enums[name];
};
const merge = (...maps) => Object.assign({}, ...maps);
const failureLiterals = { SAIDA_INVALIDA: 'INVALID_OUTPUT', PROVEDOR_INDISPONIVEL: 'PROVIDER_UNAVAILABLE' };
const audioReasons = Object.fromEntries(Object.entries(E('AudioNotProcessableException.Reason')).map(([a, b]) => [`AUDIO_${a}`, `AUDIO_${b}`]));
const failureCodes = merge(E('ErrorCode.ErrorCode'), failureLiterals, audioReasons);
const reasonCodes = merge(api.actions.enum, E('ErrorCode.ErrorCode'), {
  VINCULO_DESFEITO: 'LINK_UNDONE',
  PRAZO_VENCIDO: 'DEADLINE_EXPIRED',
  SEM_CONFIRMACAO: 'NO_CONFIRMATION',
  MENSAGEM_NAO_EDITAVEL: 'MESSAGE_NOT_EDITABLE',
  DOCUMENTO_INDISPONIVEL: 'DOCUMENT_UNAVAILABLE',
  CHAT_INDISPONIVEL: 'CHAT_UNAVAILABLE',
  ERRO_TELEGRAM: 'TELEGRAM_ERROR',
  ACAO_NAO_SUPORTADA: 'ACTION_NOT_SUPPORTED',
  LIMITE_DE_TAXA: 'RATE_LIMITED',
});
const promptVersions = {
  'estruturacao-aula.v1': 'lesson-structuring.v1',
  'incorporacao-respostas.v1': 'answer-incorporation.v1',
  'revisao-aula.v1': 'lesson-revision.v1',
  'consolidacao-semestral.v1': 'semester-consolidation.v1',
  'revisao-semestral.v1': 'semester-revision.v1',
  'correcao-json.v1': 'json-correction.v1',
};
const reportType = E('ReportType.ReportType');
const stepMap = E('JobStep.JobStep');

/** coluna → { pares, fechado }: `fechado` = conjunto fechado, sujeito à rede de segurança */
const COLUMNS = {
  'ai_call.report_type': [reportType, true],
  'channel_document.report_type': [reportType, true],
  'report_interaction.report_type': [reportType, true],
  'processing_job.report_type': [reportType, true],
  'report_version.report_type': [reportType, true],
  'telegram_link.focused_report_type': [reportType, true],
  'pdf_document.type': [E('DocumentType.DocumentType'), true],
  'lesson_report.status': [E('ReportStatus.ReportStatus'), true],
  'semester_report.status': [E('ReportStatus.ReportStatus'), true],
  'processing_job.status': [E('JobStatus.JobStatus'), true],
  'processing_job.type': [E('JobType.JobType'), true],
  'processing_job.step': [stepMap, true],
  'processing_job.result_code': [E('JobResult.JobResult'), true],
  'lesson_report.processing_step': [stepMap, true],
  'semester_report.processing_step': [stepMap, true],
  'processing_job.medium': [E('Medium.Medium'), true],
  'report_interaction.medium': [E('Medium.Medium'), true],
  'telegram_link.flow': [E('ConversationFlow.ConversationFlow'), true],
  'telegram_link.flow_step': [E('ConversationFlowStep.ConversationFlowStep'), true],
  'channel_action.state': [E('ChannelActionState.ChannelActionState'), true],
  'channel_action.type': [E('ChannelActionType.ChannelActionType'), true],
  'channel_action.origin': [E('ChannelActionOrigin.ChannelActionOrigin'), true],
  'channel_action.reason_code': [reasonCodes, false],
  'processed_telegram_update.origin': [{ INTERNO: 'INTERNAL' }, true],
  'report_interaction.type': [E('InteractionType.InteractionType'), true],
  'report_interaction.result': [E('InteractionResult.InteractionResult'), true],
  'report_version.origin': [E('VersionOrigin.VersionOrigin'), true],
  'report_version.prompt_version': [promptVersions, true],
  'ai_call.task': [E('AiTask.AiTask'), true],
  'lesson_report.failure_reason': [failureCodes, false],
  'ai_call.error_code': [failureCodes, false],
};

const quote = (text) => `'${text.replace(/'/g, "''")}'`;
const columnLimit = new Map();
for (const table of catalog.tables) {
  for (const column of table.columns) {
    const limit = column.type.match(/character varying\((\d+)\)/);
    if (limit) columnLimit.set(`${table.name}.${column.name}`, Number(limit[1]));
  }
}
const problems = [];
for (const key of Object.keys(COLUMNS)) if (!columnLimit.has(key)) problems.push(`coluna fora do catálogo da V7: ${key}`);
for (const [key, [pairs]] of Object.entries(COLUMNS)) {
  const limit = columnLimit.get(key);
  for (const novo of Object.values(pairs)) if (limit && novo.length > limit) problems.push(`${key}: "${novo}" excede varchar(${limit})`);
}
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}

const sql = [];
sql.push(
  '-- Fatia 4 da feature 003: valores de enumeração em inglês (os nomes de tabela, coluna e chave já são da V7).',
  '-- Os valores são os de `identifier-map.md` §2; chaves e textos do JSON do fio acompanham os contratos novos.',
  '-- A migração roda em uma transação e ABORTA se sobrar valor antigo em coluna de conjunto fechado (parte 5).',
  '',
);

// ---- parte 1: descartar os objetos com literal de enumeração ---------------------------------------------------------
const literal = /'([A-Z][A-Z0-9_]+)'/g;
const wanted = [];
for (const index of catalog.indexes) if (/'[A-Z][A-Z0-9_]{2,}'/.test(index.definition) && !/PRIMARY|pkey/.test(index.name)) wanted.push({ kind: 'index', ...index });
for (const constraint of catalog.constraints) if (/'[A-Z][A-Z0-9_]{2,}'/.test(constraint.definition ?? '')) wanted.push({ kind: 'constraint', ...constraint });
// quais literais cada objeto contém, e o mapa que vale para o objeto (pela coluna citada)
const globalPairs = merge(
  reportType,
  E('ReportStatus.ReportStatus'),
  E('JobStatus.JobStatus'),
  E('JobType.JobType'),
  E('ChannelActionState.ChannelActionState'),
  E('ChannelActionType.ChannelActionType'),
  E('ChannelActionOrigin.ChannelActionOrigin'),
  { INTERNO: 'INTERNAL' },
);
sql.push('-- 1. Objetos cujo predicado tem literal de enumeração: descartados aqui e recriados na parte 4, com os mesmos nomes.');
for (const object of wanted) {
  if (object.kind === 'constraint') sql.push(`ALTER TABLE ${object.table} DROP CONSTRAINT ${object.name};`);
  else sql.push(`DROP INDEX ${object.name};`);
}
sql.push('');

// ---- parte 2: valores, coluna a coluna ---------------------------------------------------------------------------------
sql.push('-- 2. Valores das colunas, uma coluna por vez (os conjuntos de cada coluna estão em `identifier-map.md` §2).');
for (const [key, [pairs]] of Object.entries(COLUMNS)) {
  const [table, column] = key.split('.');
  const values = Object.entries(pairs)
    .map(([antigo, novo]) => `(${quote(antigo)}, ${quote(novo)})`)
    .join(', ');
  sql.push(`UPDATE ${table} AS target SET ${column} = changed.novo FROM (VALUES ${values}) AS changed(antigo, novo) WHERE target.${column} = changed.antigo;`);
}
sql.push('');

// ---- parte 3: JSON do fio ---------------------------------------------------------------------------------------------
const actionKeys = api.actions.property;
const actionTypes = E('ChannelActionType.ChannelActionType');
sql.push(
  '-- 3. JSON do fio: chaves de `payload` e `compensation` pelo esquema novo das ações; valor de `type` e ação do botão (`data`)',
  '--    pelos vocabulários novos. `text` e `caption` (texto do professor) nunca são lidos nem alterados.',
  'CREATE FUNCTION pg_temp.rename_wire_json(document jsonb, names jsonb, types jsonb, actions jsonb) RETURNS jsonb',
  'LANGUAGE sql IMMUTABLE AS $$',
  '  SELECT CASE jsonb_typeof(document)',
  "    WHEN 'object' THEN COALESCE((",
  '      SELECT jsonb_object_agg(',
  '        COALESCE(names ->> entry.key, entry.key),',
  '        CASE',
  "          WHEN entry.key = 'tipo' AND jsonb_typeof(entry.value) = 'string'",
  "            THEN COALESCE(types -> (entry.value #>> '{}'), entry.value)",
  "          WHEN entry.key = 'dados' AND jsonb_typeof(entry.value) = 'string' AND (entry.value #>> '{}') ~ '^v1:[a-z]+:'",
  "            THEN to_jsonb('v1:' || COALESCE(actions ->> split_part(entry.value #>> '{}', ':', 2), split_part(entry.value #>> '{}', ':', 2))",
  "              || substr(entry.value #>> '{}', length('v1:' || split_part(entry.value #>> '{}', ':', 2)) + 1))",
  '          ELSE pg_temp.rename_wire_json(entry.value, names, types, actions)',
  '        END)',
  "        FROM jsonb_each(document) AS entry), '{}'::jsonb)",
  "    WHEN 'array' THEN COALESCE((",
  '      SELECT jsonb_agg(pg_temp.rename_wire_json(element.value, names, types, actions) ORDER BY element.position)',
  "        FROM jsonb_array_elements(document) WITH ORDINALITY AS element(value, position)), '[]'::jsonb)",
  '    ELSE document',
  '  END',
  '$$;',
  '',
  'UPDATE channel_action',
  `   SET payload = pg_temp.rename_wire_json(payload, ${quote(JSON.stringify(actionKeys))}::jsonb, ${quote(JSON.stringify(actionTypes))}::jsonb, ${quote(JSON.stringify(actions))}::jsonb)`,
  ' WHERE payload IS NOT NULL;',
  'UPDATE channel_action',
  `   SET compensation = pg_temp.rename_wire_json(compensation, ${quote(JSON.stringify(actionKeys))}::jsonb, ${quote(JSON.stringify(actionTypes))}::jsonb, ${quote(JSON.stringify(actions))}::jsonb)`,
  ' WHERE compensation IS NOT NULL;',
  '',
  '-- confirmação pendente guardada em `flow_data`: o token é a ação de botão (`aprovar:<versão>`, `cancelar`, `sair`, `descartar`)',
  'UPDATE telegram_link',
  "   SET flow_data = jsonb_set(flow_data, '{confirmation}', to_jsonb(CASE",
  "         WHEN flow_data ->> 'confirmation' LIKE 'aprovar:%' THEN 'approve:' || substr(flow_data ->> 'confirmation', 9)",
  "         WHEN flow_data ->> 'confirmation' = 'cancelar' THEN 'cancel'",
  "         WHEN flow_data ->> 'confirmation' = 'sair' THEN 'sign-out'",
  "         WHEN flow_data ->> 'confirmation' = 'descartar' THEN 'discard'",
  "         ELSE flow_data ->> 'confirmation' END))",
  " WHERE flow_data ? 'confirmation' AND jsonb_typeof(flow_data -> 'confirmation') = 'string';",
  '',
);

// ---- parte 4: recriar ----------------------------------------------------------------------------------------------------
// O banco mostra `IN ('A','B')` de coluna varchar como `= ANY ((ARRAY['A'::character varying, …])::text[])`; reescrito assim,
// o PostgreSQL volta a mostrar o mesmo texto depois de recriar. Por isso a definição volta à forma de origem (`IN (…)` e
// `= 'X'`) antes de recriar, e a catalogação da V8 só difere da da V7 nos literais.
const originalForm = (text) =>
  text
    .replace(/\(\((\w+)\)::text = ANY \(\(ARRAY\[(.+?)\]\)::text\[\]\)\)/g, (all, column, list) => `${column} IN (${list.replace(/::character varying/g, '')})`)
    .replace(/\(\((\w+)\)::text (=|<>) '([A-Za-z_]+)'::text\)/g, (all, column, operator, value) => `${column} ${operator} '${value}'`)
    .replace(/\((\w+)\)::text (=|<>) '([A-Za-z_]+)'::text/g, (all, column, operator, value) => `${column} ${operator} '${value}'`);
const replaceLiterals = (text) =>
  originalForm(
    text.replace(literal, (all, value) => {
      if (!Object.hasOwn(globalPairs, value)) throw new Error(`literal sem par na recriação: ${value} em ${text}`);
      return `'${globalPairs[value]}'`;
    }),
  );
sql.push('-- 4. Objetos recriados, com os mesmos nomes e os literais novos.');
for (const object of wanted) {
  if (object.kind === 'constraint') sql.push(`ALTER TABLE ${object.table} ADD CONSTRAINT ${object.name} ${replaceLiterals(object.definition)};`);
  else sql.push(`${replaceLiterals(object.definition.replace(' ON public.', ' ON '))};`);
}
sql.push('');

// ---- parte 5: rede de segurança -----------------------------------------------------------------------------------------
sql.push('-- 5. Rede de segurança: nenhum valor fora do conjunto novo em coluna de conjunto fechado, e cada valor cabe na coluna.');
sql.push('DO $$', 'DECLARE', '  found bigint;', 'BEGIN');
for (const [key, [pairs, closed]] of Object.entries(COLUMNS)) {
  if (!closed) continue;
  const [table, column] = key.split('.');
  const allowed = [...new Set(Object.values(pairs).concat(Object.entries(pairs).filter(([a, b]) => a === b).map(([a]) => a)))];
  // valores que não mudam de nome (ex.: `AUDIO`, `PRONTO`→`READY` muda; `WEB` não) também são válidos
  sql.push(`  SELECT count(*) INTO found FROM ${table} WHERE ${column} IS NOT NULL AND ${column} NOT IN (${allowed.map(quote).join(', ')}${extraUnchanged(key)});`);
  sql.push(`  IF found > 0 THEN RAISE EXCEPTION 'V8: % valor(es) fora do conjunto novo em ${key}', found; END IF;`);
}
sql.push('END $$;', '');

function extraUnchanged(key) {
  // valores que já eram iguais nos dois idiomas e, por isso, não estão nos pares
  const same = {
    'processing_job.medium': ['AUDIO'],
    'report_interaction.medium': ['AUDIO'],
    'pdf_document.type': [],
    'report_interaction.type': [],
    'processing_job.type': [],
    'telegram_link.flow': [],
  };
  return (same[key] ?? []).map((value) => `, ${quote(value)}`).join('');
}

writeFileSync(join(root, 'backend/application/src/main/resources/db/migration/V8__rename_enum_values.sql'), `${sql.join('\n')}\n`);
const values = Object.fromEntries(Object.entries(COLUMNS).map(([key, [pairs, closed]]) => [key, { closed, pairs, limit: columnLimit.get(key) }]));
writeFileSync(
  join(root, 'backend/application/src/test/resources/db/v8-values.json'),
  `${JSON.stringify({ descricao: 'Valores de enumeração da V8, por tabela.coluna (gerado por tools/gen-v8.mjs)', columns: values, objects: wanted.map((o) => ({ kind: o.kind, table: o.table, name: o.name })), actionKeys, actionTypes, callbackActions: actions, literalPairs: globalPairs }, null, 2)}\n`,
);
console.log(`V8: ${wanted.length} objetos recriados, ${Object.keys(COLUMNS).length} colunas`);
