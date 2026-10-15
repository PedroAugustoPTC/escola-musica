// Testes da conferência do catálogo de mensagens (T056):
// `node --test specs/003-migrate-code-to-english/tools/test/catalog.test.mjs`
// Os termos em português são dados de teste do verificador (o guardião não varre `specs/`).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { compareCatalogs, compareJsonMessages, parseCatalog, swapMarkers } from '../lib/catalog.mjs';

const BASE = parseCatalog('# cabeçalho\nSAUDACAO=Olá, {nome}\nSEM_ALUNOS=Você ainda não tem alunos.\nBLOQUEIO=Tente em {minutos} minuto(s).\n');
const KEYS = { SAUDACAO: 'GREETING', SEM_ALUNOS: 'NO_STUDENTS', BLOQUEIO: 'BLOCKED' };
const MARKERS = { nome: 'name', minutos: 'minutes' };
const kinds = (errors) => errors.map((error) => error.kind);

test('parseCatalog ignora comentários e linhas em branco e preserva o valor byte a byte', () => {
  assert.deepEqual(BASE.keys, ['SAUDACAO', 'SEM_ALUNOS', 'BLOQUEIO']);
  assert.equal(BASE.entries.get('SAUDACAO'), 'Olá, {nome}');
});

test('swapMarkers troca só o que está entre chaves', () => {
  assert.equal(swapMarkers('nome {nome} e {minutos}', MARKERS), 'nome {name} e {minutes}');
});

test('aprova troca só de chave e de marcador', () => {
  const novo = parseCatalog('GREETING=Olá, {name}\nNO_STUDENTS=Você ainda não tem alunos.\nBLOCKED=Tente em {minutes} minuto(s).\n');
  assert.deepEqual(compareCatalogs(BASE, novo, { keyMap: KEYS, markerMap: MARKERS }), []);
});

test('sem mapa de chaves o catálogo tem que ser idêntico (fatia que só move o arquivo)', () => {
  assert.deepEqual(compareCatalogs(BASE, parseCatalog('SAUDACAO=Olá, {nome}\nSEM_ALUNOS=Você ainda não tem alunos.\nBLOQUEIO=Tente em {minutos} minuto(s).\n')), []);
});

test('reprova chave faltando', () => {
  const novo = parseCatalog('GREETING=Olá, {name}\nNO_STUDENTS=Você ainda não tem alunos.\n');
  assert.ok(kinds(compareCatalogs(BASE, novo, { keyMap: KEYS, markerMap: MARKERS })).includes('chave-faltando'));
});

test('reprova chave sobrando', () => {
  const novo = parseCatalog('GREETING=Olá, {name}\nNO_STUDENTS=Você ainda não tem alunos.\nBLOCKED=Tente em {minutes} minuto(s).\nEXTRA=x\n');
  const errors = kinds(compareCatalogs(BASE, novo, { keyMap: KEYS, markerMap: MARKERS }));
  assert.ok(errors.includes('chave-sobrando'));
  assert.ok(errors.includes('total-diferente'));
});

test('reprova texto alterado', () => {
  const novo = parseCatalog('GREETING=Olá, {name}!\nNO_STUDENTS=Você ainda não tem alunos.\nBLOCKED=Tente em {minutes} minuto(s).\n');
  assert.deepEqual(kinds(compareCatalogs(BASE, novo, { keyMap: KEYS, markerMap: MARKERS })), ['texto-alterado']);
});

test('reprova marcador fora do mapa', () => {
  const novo = parseCatalog('GREETING=Olá, {nome}\nNO_STUDENTS=Você ainda não tem alunos.\nBLOCKED=Tente em {minutes} minuto(s).\n');
  const errors = kinds(compareCatalogs(BASE, novo, { keyMap: KEYS, markerMap: MARKERS }));
  assert.ok(errors.includes('texto-alterado'));
  assert.ok(errors.includes('marcador-fora-do-mapa'));
});

test('reprova duas chaves antigas para a mesma chave nova (bijeção)', () => {
  const novo = parseCatalog('GREETING=Olá, {name}\nBLOCKED=Tente em {minutes} minuto(s).\n');
  assert.ok(kinds(compareCatalogs(BASE, novo, { keyMap: { ...KEYS, SEM_ALUNOS: 'GREETING' }, markerMap: MARKERS })).includes('chave-repetida-no-mapa'));
});

test('o mensagens.json gerado passa pela mesma comparação', () => {
  const antigo = { SAUDACAO: 'Olá, {nome}' };
  assert.deepEqual(compareJsonMessages(antigo, { GREETING: 'Olá, {name}' }, { keyMap: KEYS, markerMap: MARKERS }), []);
  assert.deepEqual(kinds(compareJsonMessages(antigo, { GREETING: 'Oi, {name}' }, { keyMap: KEYS, markerMap: MARKERS })), ['texto-alterado']);
});
