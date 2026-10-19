// Testes do gerador de token (feature 004): 32 bytes aleatórios em base64url, sem escrever em disco.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const script = join(dirname(fileURLToPath(import.meta.url)), 'generate-token.mjs');

function generate(cwd) {
  return spawnSync('node', [script], { encoding: 'utf8', cwd });
}

test('gera 32 bytes aleatórios em base64url, uma linha', () => {
  const run = generate(tmpdir());

  assert.equal(run.status, 0, run.stderr);
  assert.match(run.stdout, /^[A-Za-z0-9_-]{43}\n$/);
  assert.equal(Buffer.from(run.stdout.trim(), 'base64url').length, 32);
});

test('nunca repete', () => {
  const tokens = new Set(Array.from({ length: 20 }, () => generate(tmpdir()).stdout));

  assert.equal(tokens.size, 20);
});

test('não escreve nada em disco e não imprime em stderr', () => {
  const directory = mkdtempSync(join(tmpdir(), 'token-'));

  const run = generate(directory);

  assert.deepEqual(readdirSync(directory), []);
  assert.equal(run.stderr, '');
});
