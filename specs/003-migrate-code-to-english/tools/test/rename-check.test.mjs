// Testes do rename-check (T031): `node --test specs/003-migrate-code-to-english/tools/test`
// Fixtures sintéticas: mapa e retenção mínimos em uma raiz temporária. Os termos em português usados aqui são dados de
// teste do verificador (o guardião de idioma não varre a pasta `specs/`).
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { alignJava, collisionsOf } from '../lib/align.mjs';
import { loadRenameMap } from '../lib/rename-map.mjs';

const PACKAGES = {
  'br.com.felipeblaft.relatorios': 'br.com.felipeblaft.reports',
  'br.com.felipeblaft.relatorios.dominio': 'br.com.felipeblaft.reports.domain',
  'x.inicio': 'x.home',
  'a.dominio': 'a.domain',
  'b.relatorios': 'b.reports',
};

function fixtureRoot({ map, held = { B1: {}, B2: {}, B3: {}, permanent: [] }, explicit = {} }) {
  const root = mkdtempSync(join(tmpdir(), 'rename-check-'));
  const tools = join(root, 'specs/003-migrate-code-to-english/tools');
  mkdirSync(join(tools, 'maps'), { recursive: true });
  mkdirSync(join(tools, 'held-back'), { recursive: true });
  mkdirSync(join(tools, 'rewrite'), { recursive: true });
  writeFileSync(join(tools, 'rewrite/slice-2-packages.json'), JSON.stringify(PACKAGES));
  writeFileSync(join(tools, 'maps/slice-2.json'), JSON.stringify(Object.entries(map).map(([antigo, novo]) => ({ antigo, novo }))));
  writeFileSync(join(tools, 'explicit-pairs.json'), JSON.stringify({ pathSegments: {}, identifiers: {}, literals: [], ...explicit }));
  writeFileSync(join(tools, 'held-back/slice-2.json'), JSON.stringify(held));
  return root;
}

const MAP = {
  RelatorioAula: 'LessonReport',
  AlunoRepositorio: 'StudentRepository',
  aluno: 'student',
  alunoNome: 'studentName',
  emAberto: 'open',
  aberto: 'open',
  dominio: 'domain',
  relatorios: 'reports',
  buscar: 'search',
  inicio: 'start',
  nome: 'name',
  criadoEm: 'createdAt',
};
const root = fixtureRoot({ map: MAP });
const map = loadRenameMap(root, 2);
const check = (base, work, file = 'X.java') => alignJava(base, work, { map, file });
const kinds = (result) => result.errors.map((error) => error.kind);

test('aprova par do mapa, inclusive pacote e tipo', () => {
  const base = 'package br.com.felipeblaft.relatorios.dominio;\nclass RelatorioAula { String aluno; }';
  const work = 'package br.com.felipeblaft.reports.domain;\nclass LessonReport { String student; }';
  assert.deepEqual(check(base, work).errors, []);
});

test('aprova a troca de ordem dos imports (o Spotless reordena)', () => {
  const base = 'import a.dominio.Z;\nimport b.relatorios.AlunoRepositorio;\nclass A {}';
  const work = 'import b.reports.StudentRepository;\nimport a.domain.Z;\nclass A {}';
  assert.deepEqual(check(base, work).errors, []);
});

test('pacote usa a tabela por pacote inteiro (x.inicio → x.home) e o identificador usa o mapa (inicio → start)', () => {
  const base = 'package x.inicio;\nclass A { int inicio; }';
  const work = 'package x.home;\nclass A { int start; }';
  assert.deepEqual(check(base, work).errors, []);
  assert.deepEqual(kinds(check('package x.inicio;', 'package x.start;')), ['par']);
});

test('reprova par fora do mapa', () => {
  const result = check('class A { int aluno; }', 'class A { int pupil; }');
  assert.deepEqual(kinds(result), ['par']);
});

test('reprova literal de texto alterado, mas aceita o literal idêntico (nome do PDF exibido)', () => {
  const same = '"relatorio-aula-" + data + ".pdf"';
  assert.deepEqual(check(`class A { String n = ${same}; }`, `class A { String n = ${same}; }`).errors, []);
  const result = check('class A { String t = "Aluno aprovado."; }', 'class A { String t = "Student approved."; }');
  assert.deepEqual(kinds(result), ['literal']);
});

test('literal de uma palavra só muda se o par está no mapa', () => {
  assert.deepEqual(check('class A { String k = "aluno"; }', 'class A { String k = "student"; }').errors, []);
  assert.deepEqual(kinds(check('class A { String k = "aluno"; }', 'class A { String k = "pupil"; }')), ['literal']);
});

test('reprova número alterado e operador alterado', () => {
  assert.deepEqual(kinds(check('class A { int x = 5; }', 'class A { int x = 6; }')), ['token']);
  assert.deepEqual(kinds(check('class A { boolean x = 1 < 2; }', 'class A { boolean x = 1 > 2; }')), ['token']);
});

test('reprova número de tokens diferente', () => {
  assert.deepEqual(kinds(check('class A { int x; }', 'class A { int x; int y; }')), ['tokens']);
});

test('detecta colisão: dois nomes antigos viram o mesmo nome novo no mesmo arquivo', () => {
  const result = check('class A { int emAberto; int aberto; }', 'class A { int open; int open; }');
  assert.deepEqual(result.errors, []);
  const collisions = collisionsOf(result, 'A.java');
  assert.equal(collisions.length, 1);
  assert.deepEqual(collisions[0].antigos, ['aberto', 'emAberto']);
});

test('detecta renomeação sobre um nome que já existia (colisão com nome em inglês)', () => {
  const result = check('class A { int aluno; int student; }', 'class A { int student; int student; }');
  assert.equal(collisionsOf(result, 'A.java').length, 1);
});

test('identificador retido (B1/B2) que mudou reprova; o retido igual aprova', () => {
  const held = {
    B1: {},
    B2: { 'Conteudo': { file: 'x', members: ['nome'] } },
    B3: {},
    permanent: [],
  };
  const heldMap = loadRenameMap(fixtureRoot({ map: MAP, held }), 2);
  const base = 'record Conteudo(String nome) {}';
  const bad = alignJava(base, 'record Conteudo(String name) {}', { map: heldMap, file: 'C.java' });
  assert.deepEqual(kinds(bad), ['retido-alterado']);
  assert.deepEqual(alignJava(base, base, { map: heldMap, file: 'C.java' }).errors, []);
});

test('aprova identificador renomeado dentro de código embutido (JPQL e JSON em bloco de texto)', () => {
  const base = 'class A { String q = """\n  {"aluno": 1, "nome": 2}\n  """; }';
  const work = 'class A { String q = """\n  {"student": 1, "name": 2}\n  """; }';
  assert.deepEqual(check(base, work).errors, []);
  assert.deepEqual(kinds(check(base, 'class A { String q = """\n  {"pupil": 1}\n  """; }')), ['literal']);
});

test('edição manual de literal só passa se estiver em explicit-pairs.json com motivo', () => {
  const allowedRoot = fixtureRoot({
    map: MAP,
    explicit: { literals: [{ file: 'Rules.java', from: 'pacote.casodeuso', to: 'pacote.usecase', reason: 'regra ArchUnit' }] },
  });
  const allowedMap = loadRenameMap(allowedRoot, 2);
  const base = 'class A { String p = "pacote.casodeuso"; }';
  const work = 'class A { String p = "pacote.usecase"; }';
  assert.deepEqual(alignJava(base, work, { map: allowedMap, file: 'x/Rules.java' }).errors, []);
  assert.deepEqual(kinds(alignJava(base, work, { map, file: 'x/Rules.java' })), ['literal']);
});

test('comentários alterados só entram na contagem informativa', () => {
  const result = check('class A { /* aluno */ int x; }', 'class A { /* student */ int x; }');
  assert.deepEqual(result.errors, []);
  assert.equal(result.commentsChanged, 1);
});

test('troca manual de identificador só passa no arquivo e com o par listados em explicit-pairs.json', () => {
  const editRoot = fixtureRoot({
    map: MAP,
    explicit: { tokenEdits: [{ file: 'Rules.java', from: 'startsWith', to: 'endsWith', reason: 'prefixo virou sufixo' }] },
  });
  const editMap = loadRenameMap(editRoot, 2);
  const base = 'class A { boolean b = x.startsWith("Entrada"); }';
  const work = 'class A { boolean b = x.endsWith("Entrada"); }';
  assert.deepEqual(alignJava(base, work, { map: editMap, file: 'x/Rules.java' }).errors, []);
  assert.deepEqual(kinds(alignJava(base, work, { map: editMap, file: 'x/Other.java' })), ['par']);
});
