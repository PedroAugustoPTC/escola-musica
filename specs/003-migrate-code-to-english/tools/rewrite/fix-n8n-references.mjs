#!/usr/bin/env node
// Fatia 7 (T207 a T212 e T220): a "feature 003" que os documentos da spec 002 e dois comentários de código citam como
// "a que traz o n8n" passou a ser a 004, porque o número 003 ficou com a migração para inglês. Troca só o número e só
// nas expressões que significam n8n; nunca `FR-003`, `SC-003`, `T003` nem os números de task.
//
//   node fix-n8n-references.mjs [--dry-run]
import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const root = resolve(process.cwd());
const dry = process.argv.includes('--dry-run');
const docs = ['spec.md', 'plan.md', 'research.md', 'quickstart.md', 'tasks.md', 'contracts/canal-telegram-interno.md'].map((name) => `specs/002-api-interna-canal-telegram/${name}`);
const code = [
  'backend/application/src/main/resources/application.yaml',
  'backend/application/src/test/java/br/com/felipeblaft/reports/support/SimulatedActionsExecutorTest.java',
  'infra/.env.example',
];

// "feature `003`", "feature 003", "da `003`", "para a `003`", "depois da `003`", "n8n da `003`", "workflow da 003"
const patterns = [
  [/(feature )(`?)003(`?)/g, '$1$2004$3'],
  [/((?:da|para a|depois da|a) )`003`/g, '$1`004`'],
  [/(workflow da )003\b/g, '$1004'],
];

let total = 0;
for (const file of [...docs, ...code]) {
  const original = readFileSync(join(root, file), 'utf8');
  const lines = original.split('\n');
  const changed = lines.map((line, index) => {
    let out = line;
    for (const [pattern, replacement] of patterns) out = out.replace(pattern, replacement);
    if (out !== line) {
      total += 1;
      console.log(`${file}:${index + 1}: ${line.trim().slice(0, 110)}\n    → ${out.trim().slice(0, 110)}`);
    }
    return out;
  });
  if (!dry) writeFileSync(join(root, file), changed.join('\n'));
}
console.log(`${dry ? '[ensaio] ' : ''}${total} linhas`);
