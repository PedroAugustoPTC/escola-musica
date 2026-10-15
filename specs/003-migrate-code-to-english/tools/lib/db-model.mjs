// Lê `data-model.md` (mapa de renomeação de tabelas e colunas) como dados (T058, T065, T083).
//
//   const model = loadDbModel(root);
//   model.tables            Map(tabelaAntiga → tabelaNova)
//   model.columns           Map(tabelaAntiga → Map(colunaAntiga → colunaNova))
//   model.column(tabela, c) coluna nova de `c` em `tabela` (as não listadas não mudam)
//   model.camel(snake)      user_id → userId
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const camel = (snake) => snake.replace(/_([a-z0-9])/g, (_, letter) => letter.toUpperCase());

export function loadDbModel(root) {
  const text = readFileSync(join(root, 'specs/003-migrate-code-to-english/data-model.md'), 'utf8');
  const tables = new Map();
  const columns = new Map();
  let section = null; // 'tables' | tabela antiga atual
  let currentOld = null;
  for (const line of text.split(/\r?\n/)) {
    if (/^## Tabelas/.test(line)) section = 'tables';
    else if (/^## Colunas/.test(line)) section = 'columns';
    else if (/^## Verifica/.test(line)) section = null;
    const heading = line.match(/^### `([a-z_]+)` \(era `([a-z_]+)`\)/);
    if (heading) {
      currentOld = heading[2];
      columns.set(currentOld, new Map());
      continue;
    }
    if (section === 'tables') {
      const row = line.match(/^\| `([a-z_]+)` \| `([a-z_]+)` \|/);
      if (row) tables.set(row[1], row[2]);
    } else if (section === 'columns' && currentOld) {
      const row = line.match(/^\| (.+?) \| (.+?) \|$/);
      if (!row || /^-+$/.test(row[1].replace(/\s/g, '')) || row[1].includes('Atual')) continue;
      const olds = [...row[1].matchAll(/`([a-z_0-9]+)`/g)].map((match) => match[1]);
      const news = [...row[2].matchAll(/`([a-z_0-9]+)`/g)].map((match) => match[1]);
      olds.forEach((old, index) => columns.get(currentOld).set(old, news[index]));
    }
  }
  return {
    tables,
    columns,
    column: (table, name) => columns.get(table)?.get(name) ?? name,
    camel,
  };
}
