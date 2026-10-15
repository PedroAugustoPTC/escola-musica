#!/usr/bin/env node
// Fatia 7 (T207 a T219, FR-029): acrescenta a nota de histórico (texto padrão do tasks.md) no topo dos documentos das
// specs 001 e 002, logo abaixo do título (ou do bloco de metadados `---` das listas de tarefas). Idempotente.
//
//   node add-history-note.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const root = resolve(process.cwd());
const files = {
  'specs/001-fluxo-relatorios': ['spec.md', 'plan.md', 'research.md', 'data-model.md', 'quickstart.md', 'tasks.md'],
  'specs/002-api-interna-canal-telegram': ['spec.md', 'plan.md', 'research.md', 'data-model.md', 'quickstart.md', 'tasks.md'],
};
const MARK = '> **Nota de histórico (feature 003)**';
const note = `${MARK}: este documento foi escrito antes da migração do código para
> inglês e cita nomes anteriores a ela (classes, arquivos, tabelas, rotas, variáveis). Ele permanece como
> histórico, sem reescrita. Para o nome atual de cada termo, consulte o glossário em
> [\`.specify/memory/glossary.json\`](../../.specify/memory/glossary.json) e o Princípio VI da constituição.`;

let changed = 0;
for (const [folder, names] of Object.entries(files)) {
  for (const name of names) {
    const path = join(root, folder, name);
    const text = readFileSync(path, 'utf8');
    if (text.includes(MARK)) continue;
    const lines = text.split('\n');
    let at;
    if (lines[0].trim() === '---') {
      at = lines.findIndex((line, index) => index > 0 && line.trim() === '---') + 1; // depois do bloco de metadados
    } else {
      at = 1; // depois do título
    }
    lines.splice(at, 0, '', ...note.split('\n'));
    writeFileSync(path, lines.join('\n'));
    changed += 1;
    console.log(`nota acrescentada: ${folder}/${name} (linha ${at + 1})`);
  }
}
console.log(`${changed} arquivos`);
