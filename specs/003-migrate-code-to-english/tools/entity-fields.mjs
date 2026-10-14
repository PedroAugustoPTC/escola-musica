#!/usr/bin/env node
// Relatório dos campos das entidades JPA contra o mapa de colunas do `data-model.md` (T065).
//
//   node entity-fields.mjs [--root <raiz>] [--json]
//
// Para cada `@Entity`: tabela (`@Table`), cada campo, a coluna antiga (`@Column(name)` ou o nome em snake_case, como
// faz a estratégia de nomes do Spring), a coluna nova (data-model) e o nome de campo ESPERADO (camelCase da coluna nova).
// Compara com o nome que o mapa de identificadores da fatia daria. A diferença vira `qualifiedFields` em
// `explicit-pairs.json`: sem isso, um campo sem `@Column(name)` deixaria de casar com a coluna e o
// `ddl-auto: validate` reprovaria na subida do contexto.
import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { lex } from './lib/java-lexer.mjs';
import { loadDbModel } from './lib/db-model.mjs';
import { loadRenameMap } from './lib/rename-map.mjs';

const args = process.argv.slice(2);
const root = resolve(args.includes('--root') ? args[args.indexOf('--root') + 1] : process.cwd());
const json = args.includes('--json');
const model = loadDbModel(root);
const map = loadRenameMap(root, 3);
const snake = (name) => name.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase();

function* javaFiles(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.name === 'target') continue;
    const full = join(directory, entry.name);
    if (entry.isDirectory()) yield* javaFiles(full);
    else if (entry.name.endsWith('.java')) yield full;
  }
}

const found = [];
for (const file of javaFiles(join(root, 'backend/aplicacao/src/main/java'))) {
  const { tokens } = lex(readFileSync(file, 'utf8'));
  if (!tokens.some((token) => token.type === 'annotation' && token.value === '@Entity')) continue;
  let entity = null;
  let table = null;
  let depth = 0;
  let classDepth = null;
  let pending = []; // anotações do próximo membro
  let statement = [];
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];
    if (token.type === 'annotation') {
      const annotation = { name: token.value, args: {} };
      if (tokens[i + 1]?.value === '(') {
        let level = 0;
        for (let j = i + 1; j < tokens.length; j++) {
          if (tokens[j].value === '(') level++;
          if (tokens[j].value === ')' && --level === 0) {
            i = j;
            break;
          }
          if (tokens[j].type === 'ident' && tokens[j + 1]?.value === '=' && tokens[j + 2]?.type === 'string') annotation.args[tokens[j].value] = tokens[j + 2].value;
        }
      }
      pending.push(annotation);
      continue;
    }
    if (token.type === 'ident' && token.value === 'class' && tokens[i + 1]?.type === 'ident' && pending.some((a) => a.name === '@Entity')) {
      entity = tokens[i + 1].value;
      table = pending.find((a) => a.name === '@Table')?.args.name ?? snake(entity);
      pending = [];
    }
    if (token.value === '{' && token.type === 'op') {
      depth++;
      if (entity && classDepth === null && depth >= 1 && tokens[i - 1]?.type === 'ident') classDepth = depth;
      statement = [];
      if (depth !== classDepth) pending = [];
      continue;
    }
    if (token.value === '}' && token.type === 'op') {
      depth--;
      if (classDepth !== null && depth < classDepth) {
        classDepth = null;
        entity = null;
      }
      statement = [];
      pending = [];
      continue;
    }
    if (!entity || depth !== classDepth) continue;
    if (token.value === ';' && token.type === 'op') {
      const names = statement.filter((t) => t.type === 'ident').map((t) => t.value);
      const isMethod = statement.some((t) => t.value === '(');
      const beforeEquals = statement.slice(0, statement.findIndex((t) => t.value === '=') < 0 ? statement.length : statement.findIndex((t) => t.value === '='));
      const fieldName = beforeEquals.filter((t) => t.type === 'ident').at(-1)?.value;
      if (!isMethod && fieldName && names.length >= 2 && !pending.some((a) => a.name === '@Transient') && !beforeEquals.some((t) => t.value === 'static')) {
        const column = pending.find((a) => a.name === '@Column' || a.name === '@JoinColumn')?.args.name ?? snake(fieldName);
        found.push({ entity, table, field: fieldName, column });
      }
      statement = [];
      pending = [];
      continue;
    }
    statement.push(token);
  }
}

const report = found.map((item) => {
  const tableNew = model.tables.get(item.table) ?? item.table;
  const columnNew = model.column(item.table, item.column);
  const expected = model.camel(columnNew);
  const current = map.mapIdent(item.field);
  return { ...item, tableNew, columnNew, expected, current, ok: expected === current };
});
if (json) {
  console.log(JSON.stringify(report, null, 2));
} else {
  const bad = report.filter((row) => !row.ok);
  console.log(`${report.length} campos em ${new Set(report.map((row) => row.entity)).size} entidades; ${bad.length} com nome diferente do esperado`);
  for (const row of bad) console.log(`${row.entity}#${row.field}: coluna ${row.column} → ${row.columnNew}; esperado "${row.expected}", o mapa dá "${row.current}"`);
}
