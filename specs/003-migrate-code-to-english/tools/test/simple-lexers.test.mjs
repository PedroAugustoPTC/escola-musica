// Testes dos tokenizadores simples e do alinhamento de arquivos que não são Java (T055):
// `node --test specs/003-migrate-code-to-english/tools/test/simple-lexers.test.mjs`
// Fixtures sintéticas: os termos em português são dados de teste do verificador (o guardião não varre `specs/`).
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { alignSimple } from '../lib/align-simple.mjs';
import { loadRenameMap } from '../lib/rename-map.mjs';
import { kindOf, lexSimple } from '../lib/simple-lexers.mjs';

function fixtureMap(pairs, explicit = {}) {
  const root = mkdtempSync(join(tmpdir(), 'simple-lexers-'));
  const tools = join(root, 'specs/003-migrate-code-to-english/tools');
  mkdirSync(join(tools, 'maps'), { recursive: true });
  writeFileSync(join(tools, 'maps/slice-3.json'), JSON.stringify(Object.entries(pairs).map(([antigo, novo]) => ({ antigo, novo }))));
  writeFileSync(join(tools, 'explicit-pairs.json'), JSON.stringify({ literals: [], ...explicit }));
  return loadRenameMap(root, 3);
}

const map = fixtureMap({
  aluno: 'student',
  aluno_id: 'student_id',
  nome: 'name',
  professor: 'teacher',
  'fuso-horario': 'timezone',
  relatorios: 'reports',
  escola: 'school',
  BANCO_URL: 'DATABASE_URL',
  rodape: 'footer',
  conteudo: 'content',
});
const check = (kind, base, work, file = 'x') => alignSimple(kind, base, work, { map, file });
const kinds = (result) => result.errors.map((error) => error.kind);

test('kindOf escolhe o tipo pelo nome do arquivo', () => {
  assert.equal(kindOf('a/V7__x.sql'), 'sql');
  assert.equal(kindOf('application.yaml'), 'yaml');
  assert.equal(kindOf('infra/.env.example'), 'properties');
  assert.equal(kindOf('local.sh'), 'shell');
  assert.equal(kindOf('backend/application/Dockerfile'), 'dockerfile');
  assert.equal(kindOf('pdf/x.html'), 'html');
  assert.equal(kindOf('a.json'), 'json');
});

test('SQL: comentários ficam à parte e o literal de texto não vira identificador', () => {
  const { tokens, comments } = lexSimple('sql', "-- cria a tabela\nselect nome from aluno where nome = 'Maria';");
  assert.deepEqual(comments.map((comment) => comment.text), ['-- cria a tabela']);
  assert.deepEqual(tokens.filter((token) => token.type === 'string').map((token) => token.value), ["'Maria'"]);
  assert.deepEqual(tokens.filter((token) => token.type === 'ident').map((token) => token.value), ['select', 'nome', 'from', 'aluno', 'where', 'nome']);
});

test('YAML: chave pontilhada e com hífen é um identificador só', () => {
  const { tokens } = lexSimple('yaml', 'relatorios:\n  escola:\n    fuso-horario: ${BANCO_URL:x} # nota\n');
  assert.deepEqual(tokens.filter((token) => token.type === 'ident').map((token) => token.value), ['relatorios', 'escola', 'fuso-horario', 'BANCO_URL', 'x']);
});

test('SQL: aprova troca de identificador do mapa', () => {
  const result = check('sql', 'select nome from aluno;', 'select name from student;');
  assert.deepEqual(result.errors, []);
  assert.equal(result.pairs.get('aluno'), 'student');
});

test('SQL: reprova identificador fora do mapa', () => {
  const result = check('sql', 'select nome from aluno;', 'select name from pupil;');
  assert.deepEqual(kinds(result), ['par']);
});

test('SQL: reprova literal de texto alterado', () => {
  const result = check('sql', "select 1 where nome = 'Maria da Silva';", "select 1 where name = 'Maria Silva';");
  assert.deepEqual(kinds(result), ['literal']);
});

test('SQL: aprova coluna com sublinhado pelo mapa', () => {
  assert.deepEqual(check('sql', 'select aluno_id from t;', 'select student_id from t;').errors, []);
});

test('YAML: aprova troca de chave composta e de variável de ambiente', () => {
  const base = 'relatorios:\n  escola:\n    fuso-horario: ${BANCO_URL:x}\n';
  const work = 'reports:\n  school:\n    timezone: ${DATABASE_URL:x}\n';
  assert.deepEqual(check('yaml', base, work).errors, []);
});

test('YAML: reprova valor de texto alterado entre aspas', () => {
  const result = check('yaml', 'mensagem: "Olá, professor"\n', 'mensagem: "Olá, teacher"\n');
  assert.deepEqual(kinds(result), ['literal']);
});

test('properties: aprova nome da variável e reprova troca no texto do valor', () => {
  assert.deepEqual(check('properties', 'BANCO_URL=jdbc:x\n', 'DATABASE_URL=jdbc:x\n').errors, []);
  assert.ok(check('properties', 'BANCO_URL=jdbc:x\n', 'DATABASE_URL=jdbc:y\n').errors.length > 0);
});

test('shell: comentário alterado não reprova; aspa é literal', () => {
  const result = check('shell', '# usa o aluno\necho "aluno"\n', '# usa o student\necho "aluno"\n');
  assert.deepEqual(result.errors, []);
  assert.equal(result.commentsChanged, 1);
});

test('JSON: troca só a chave; valor de texto tem que ficar igual', () => {
  assert.deepEqual(check('json', '{"nome": "aluno"}', '{"name": "aluno"}').errors, []);
  assert.deepEqual(kinds(check('json', '{"nome": "aluno"}', '{"name": "student"}')), ['literal']);
});

test('HTML: classe e expressão pelo mapa; texto visível idêntico', () => {
  const base = '<div class="rodape" th:text="${aluno}">Aluno</div>';
  assert.deepEqual(check('html', base, '<div class="footer" th:text="${student}">Aluno</div>').errors, []);
  assert.deepEqual(kinds(check('html', base, '<div class="footer" th:text="${student}">Student</div>')), ['token']);
});

test('número de tokens diferente reprova de uma vez', () => {
  assert.deepEqual(kinds(check('sql', 'select 1;', 'select 1, 2;')), ['tokens']);
});

test('exceção listada em explicit-pairs com motivo é aceita e contada', () => {
  const custom = fixtureMap({}, { literals: [{ file: 'x', from: "'a'", to: "'b'", reason: 'teste' }] });
  const result = alignSimple('sql', "select 'a';", "select 'b';", { map: custom, file: 'x' });
  assert.deepEqual(result.errors, []);
  assert.equal(result.manualLiterals.length, 1);
});
