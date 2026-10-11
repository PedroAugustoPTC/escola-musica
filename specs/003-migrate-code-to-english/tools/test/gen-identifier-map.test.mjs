// Testes do gerador do mapa de identificadores (T007). Rodar no contêiner:
//   docker run --rm -v "$PWD":/work -w /work node:24-alpine node --test specs/003-migrate-code-to-english/tools/test/
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { convertCase, translateIdentifier } from '../gen-identifier-map.mjs';
import { loadGlossary } from '../lib/words.mjs';

const glossary = loadGlossary(new URL('../../../../.specify/memory/glossary.json', import.meta.url).pathname);

function translate(identifier, options) {
  return translateIdentifier(identifier, glossary, options);
}

test('inverte substantivo e adjetivo em PascalCase', () => {
  assert.equal(translate('RelatorioAula')?.novo, 'LessonReport');
  assert.equal(translate('RelatorioAula')?.needsReview, true);
});

test('inverte em snake_case e preserva a palavra em inglês no fim', () => {
  assert.equal(translate('codigo_acesso_hash')?.novo, 'access_code_hash');
});

test('dataNascimento vira birthDate: "data" encostada em palavra portuguesa é "date"', () => {
  assert.equal(translate('dataNascimento')?.novo, 'birthDate');
});

test('"em" no fim vira "at" e o substantivo vira particípio', () => {
  assert.equal(translate('ultimo_uso_em')?.novo, 'last_used_at');
  assert.equal(translate('criado_em')?.novo, 'created_at');
  assert.equal(translate('expira_em')?.novo, 'expires_at');
});

test('"inicio" depende do contexto: tela inicial é HOME e período é START', () => {
  assert.equal(translate('INICIO', { context: 'screen' })?.novo, 'HOME');
  assert.equal(translate('INICIO', { context: 'period' })?.novo, 'START');
});

test('"inicio" sem contexto exige revisão', () => {
  const result = translate('INICIO');
  assert.equal(result?.needsReview, true);
  assert.match(result?.motivo ?? '', /contextual/);
});

test('"registro": log ou custo é record; aula é registration', () => {
  assert.equal(translate('RegistroCustoPorta')?.novo, 'CostRecordPort');
  assert.equal(translate('REGISTRO_AULA')?.novo, 'LESSON_REGISTRATION');
  assert.equal(translate('RegistroCustoPorta')?.needsReview, true);
});

test('"gravar" persistir é save e mantém o verbo na frente', () => {
  assert.equal(translate('GravarAcoesCanal')?.novo, 'SaveChannelActions');
});

test('valor com hífen vira o estilo de identificador pedido', () => {
  assert.equal(convertCase('demo-data', 'Pascal'), 'DemoData');
  assert.equal(convertCase('sign-out', 'SCREAMING'), 'SIGN_OUT');
  assert.equal(convertCase('sign-in', 'camel'), 'signIn');
});

test('substituição simples não exige revisão e respeita o plural', () => {
  assert.deepEqual([translate('alunoId')?.novo, translate('alunoId')?.needsReview], ['studentId', false]);
  assert.equal(translate('alunos')?.novo, 'students');
});

test('identificadores sem termo do glossário ficam fora do mapa', () => {
  assert.equal(translate('LessonReport'), null);
  assert.equal(translate('createdAt'), null);
});

test('compostos com ponto e barra traduzem segmento a segmento', () => {
  assert.equal(translate('dominio.relatorio')?.novo, 'domain.report');
});
