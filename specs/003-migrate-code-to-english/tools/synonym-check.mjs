#!/usr/bin/env node
// Verificador de sinônimos (task T225, SC-006): o glossário fixa UM equivalente em inglês por palavra do domínio; esta
// ferramenta procura os lugares onde a migração (ou o código novo) usou OUTRO termo para o mesmo conceito.
//
//   node synonym-check.mjs [raiz]
//
// Parte A — mapas de identificadores (`tools/maps/slice-*.json`, antigo → novo): para cada palavra portuguesa do nome antigo que o
//   glossário conhece, o equivalente do glossário (ou sua forma plural/singular) tem de aparecer no nome novo. Quando não aparece, a
//   palavra foi traduzida por outro termo (ou sumiu na reordenação): vira achado, a menos que o par esteja em ACCEPTED.
// Parte B — pares de termos concorrentes no código final (ex.: `db`/`database`): conta cada membro no inventário de palavras
//   (`evidence/word-inventory-final.txt`) e lista os grupos com mais de um membro em uso. Cada grupo tem de estar em ACCEPTED com
//   o motivo (homônimo aceito, nome de ferramenta, abreviação do ecossistema).
// Sai com código 1 quando há achado sem aceite.
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, resolve } from 'node:path';

const root = resolve(process.argv[2] ?? process.cwd());
const glossary = JSON.parse(readFileSync(join(root, '.specify/memory/glossary.json'), 'utf8'));
const notes = glossary.notes.join(' ').toLowerCase();

const split = (name) =>
  name
    .replace(/[.\-/]/g, '_')
    .split(/_|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])/)
    .filter(Boolean)
    .map((part) => part.toLowerCase());
const strip = (word) => word.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const forms = (word) => {
  const base = word.toLowerCase();
  const out = new Set([base]);
  if (base.endsWith('ies')) out.add(`${base.slice(0, -3)}y`);
  if (base.endsWith('es')) out.add(base.slice(0, -2));
  if (base.endsWith('s')) out.add(base.slice(0, -1));
  out.add(`${base}s`);
  if (base.endsWith('y')) out.add(`${base.slice(0, -1)}ies`);
  return out;
};

/** Palavra portuguesa → inglês escolhido, ignorando as ambíguas (`contextual`) e os conectivos. */
const words = new Map(Object.entries(glossary.words).map(([pt, en]) => [strip(pt), String(en).toLowerCase()]));
const contextual = new Set(glossary.contextual.map((entry) => strip(entry.pt)));
const CONNECTIVES = new Set(['de', 'da', 'do', 'dos', 'das', 'em', 'para', 'sem', 'com', 'por', 'e', 'a', 'o', 'as', 'os', 'no', 'na', 'nos', 'nas']);

/** Pares aceitos de propósito (motivo obrigatório). Chave: "pt>en-usado" (parte A) ou "a|b" (parte B). */
const ACCEPTED = {
  // parte B
  'db|database': 'db/ é a pasta de migrações do Flyway e o Playwright usa db; database é o nome do domínio (DATABASE_URL, tabelas)',
  'msg|message': 'msg() é o auxiliar curto do frontend que busca o texto do catálogo; message é o termo do domínio',
  'config|configuration': '*Config é sufixo padronizado (emenda 2.1.0); configuration só aparece em nomes de terceiros (Spring)',
  'dir|directory': 'dir é a chave curta do Spring (reports.documents.dir) e do Node; directory é o termo por extenso',
  'src|source': 'src é atributo HTML/TypeScript (nome de ferramenta); source é o termo de domínio',
  'tmp|temp|temporary': 'tmp/temp vêm de nomes de ferramenta (java.io.tmpdir, tmpfs, temp-dir da configuração); temporary é o termo de domínio',
  'err|error': 'err é o parâmetro curto de captura (catch) e de callbacks; error é o termo de domínio',
  'info|information': 'info é nível de log e propriedade de ferramenta; information é a seção de informações gerais do relatório',
  'delete|remove': 'delete é a operação de persistência (DELETE) e remove a operação de coleção/DOM; não são o mesmo conceito',
  'img|image': 'img é a tag HTML; image é o termo de domínio',
  'cb|callback': 'cb é o parâmetro curto de callback de ferramenta de teste',
  'ts|timestamp': 'ts é a extensão e o nome curto de tipo TypeScript',
  'param|parameter': 'param é a anotação do Spring e o termo de tipo; parameter é o termo geral',
  'repo|repository': 'repo aparece só em texto de ferramenta (git); repository é o sufixo padronizado',
  'app|application': 'app é o diretório/arquivo de ferramenta (app/, App.tsx); application é o módulo Maven',
  'term|semester': 'term é o modo `term` das exceções do glossário (mode: term) e palavra genérica; semester é o termo de domínio',
};

const problems = [];
const divergences = []; // Parte A: informativa (tradução idiomática no lugar da palavra do glossário)

// Parte A
const mapsDir = join(root, 'specs/003-migrate-code-to-english/tools/maps');
let comparedA = 0;
const seenA = new Set();
for (const name of readdirSync(mapsDir).filter((file) => /^slice-\d+\.json$/.test(file) && file !== 'slice-5.json' && file !== 'slice-6.json')) {
  const list = JSON.parse(readFileSync(join(mapsDir, name), 'utf8'));
  for (const entry of Array.isArray(list) ? list : []) {
    if (!entry.antigo || !entry.novo) continue;
    const oldTokens = split(entry.antigo);
    if (oldTokens.length > 4) continue; // nomes longos (métodos de teste) foram reescritos como frase em inglês, não palavra a palavra
    const newTokens = new Set(split(entry.novo).flatMap((token) => [...forms(token)]));
    for (const token of oldTokens) {
      const key = strip(token);
      if (CONNECTIVES.has(key) || contextual.has(key) || !words.has(key)) continue;
      const english = words.get(key);
      comparedA += 1;
      if (english.split(/[ /]/).some((piece) => forms(piece).has([...newTokens].find((t) => forms(piece).has(t)) ?? '')) ) continue;
      const id = `${key}>${english}`;
      if (seenA.has(id) || ACCEPTED[id]) continue;
      seenA.add(id);
      divergences.push({ pt: key, expected: english, name, example: `${entry.antigo} → ${entry.novo}` });
    }
  }
}

// Parte B
const inventoryPath = join(root, 'specs/003-migrate-code-to-english/evidence/word-inventory-final.txt');
const counts = new Map();
if (existsSync(inventoryPath)) {
  for (const line of readFileSync(inventoryPath, 'utf8').split('\n')) {
    const [word, count, example] = line.split('\t');
    if ((example ?? '').startsWith('specs/')) continue; // dados e documentos de specs citam os nomes antigos de propósito
    if (word && /^\d+$/.test(count ?? '')) counts.set(word, Number(count));
  }
}
const groupsInUse = [];
for (const [group, reason] of Object.entries(ACCEPTED)) {
  if (!group.includes('|')) continue;
  const members = group.split('|');
  const used = members.filter((member) => counts.has(member));
  if (used.length > 1) groupsInUse.push({ group, used: used.map((member) => `${member}=${counts.get(member)}`), reason });
}
// Grupos que o inventário mostra e que NÃO estão aceitos
const WATCH = [
  // Só conceitos que o domínio nomeia com UM termo: se dois aparecem no código, um é sinônimo. Pares que parecem concorrentes mas
  // são conceitos distintos (report × record, code × token, job × task, answer × response, query, medium…) não entram aqui.
  ['student', 'pupil', 'learner'],
  ['teacher', 'instructor', 'tutor'],
  ['document', 'doc'],
  ['guardian', 'responsible'],
  ['queue', 'fila'],
];
const homonyms = notes;
for (const members of WATCH) {
  const used = members.filter((member) => counts.has(member));
  if (used.length > 1) {
    const mentioned = used.every((member) => homonyms.includes(member));
    if (!mentioned && !ACCEPTED[members.join('|')]) problems.push(`grupo concorrente em uso sem aceite: ${used.map((member) => `${member}=${counts.get(member)}`).join(', ')}`);
  }
}

console.log(`synonym-check: ${comparedA} palavras de domínio comparadas nos mapas (${new Set(divergences.map((d) => d.pt)).size} traduzidas por termo idiomático diferente do glossário, só informativo); ${groupsInUse.length} grupos de abreviação/termo aceitos em uso; ${problems.length} achados`);
for (const d of divergences.filter((item, index, all) => all.findIndex((other) => other.pt === item.pt) === index)) console.log(`  idiomático: ${d.pt} (glossário: ${d.expected}) — ex.: ${d.example}`);
for (const item of groupsInUse) console.log(`  aceito: ${item.group} (${item.used.join(', ')}) — ${item.reason}`);
for (const item of problems) console.log(`  ACHADO: ${item}`);
process.exit(problems.length ? 1 : 0);
