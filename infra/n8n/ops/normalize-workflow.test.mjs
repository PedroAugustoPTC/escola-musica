// Testes do normalizador de workflows (feature 004, R-18). Rodam dentro da imagem: node --test /opt/ops
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { findUnsafeContent, normalize, serialize } from './normalize-workflow.mjs';

const script = join(dirname(fileURLToPath(import.meta.url)), 'normalize-workflow.mjs');

// valores falsos gerados na hora: nenhum arquivo versionado pode parecer um segredo
const fakeBotToken = ['123456789', 'Ab'.repeat(20)].join(':');
const fakeUrlWithPassword = ['https://operator', 'pw1234xyz@example.test/x'].join(':');

function exported() {
  return {
    updatedAt: '2026-10-15T10:00:00.000Z',
    createdAt: '2026-10-15T09:00:00.000Z',
    id: 'transport-health',
    name: 'transport-health',
    versionId: 'v-1',
    activeVersionId: 'v-1',
    active: true,
    isArchived: false,
    triggerCount: 1,
    shared: [{ role: 'workflow:owner' }],
    tags: [{ name: 'x' }],
    pinData: { Webhook: [] },
    staticData: { last: 1 },
    versionCounter: 3,
    versionMetadata: { name: null, description: null },
    sourceWorkflowId: null,
    description: null,
    nodeGroups: [],
    meta: { instanceId: 'abc', templateCredsSetupCompleted: true },
    settings: { executionOrder: 'v1' },
    nodes: [
      {
        type: 'n8n-nodes-base.webhook',
        name: 'Webhook',
        id: 'n1',
        parameters: { path: 'transport-health/live', httpMethod: 'GET' },
      },
    ],
    connections: {},
  };
}

test('remove os campos voláteis da exportação', () => {
  const result = normalize(exported());
  for (const field of [
    'createdAt',
    'updatedAt',
    'versionId',
    'activeVersionId',
    'shared',
    'tags',
    'pinData',
    'staticData',
    'active',
    'isArchived',
    'triggerCount',
    'versionCounter',
    'versionMetadata',
    'sourceWorkflowId',
  ]) {
    assert.equal(field in result, false, field);
  }
  assert.equal('instanceId' in (result.meta ?? {}), false);
  assert.equal('description' in result, false);
  assert.equal('nodeGroups' in result, false);
  assert.equal(result.id, 'transport-health');
  assert.equal(result.nodes.length, 1);
});

test('ordena as chaves de forma estável e preserva a ordem das listas', () => {
  const workflow = normalize({ ...exported(), nodes: [{ name: 'b', id: '2' }, { name: 'a', id: '1' }] });
  const text = serialize(workflow);
  assert.ok(text.indexOf('"connections"') < text.indexOf('"id"'));
  assert.ok(text.indexOf('"id"') < text.indexOf('"name"'));
  assert.deepEqual(workflow.nodes.map((node) => node.name), ['b', 'a']);
  assert.ok(text.endsWith('}\n'));
});

test('é idempotente', () => {
  const once = serialize(normalize(exported()));
  const twice = serialize(normalize(JSON.parse(once)));
  assert.equal(twice, once);
});

test('reconhece credencial, token e URL com senha sem devolver o valor', () => {
  const withCredentials = { ...exported(), nodes: [{ name: 'Call', credentials: { httpHeaderAuth: { id: '1' } } }] };
  const withToken = { ...exported(), nodes: [{ name: 'Call', parameters: { url: `https://x.test/bot${fakeBotToken}/a` } }] };
  const withPassword = { ...exported(), nodes: [{ name: 'Call', parameters: { url: fakeUrlWithPassword } }] };
  for (const workflow of [withCredentials, withToken, withPassword]) {
    const findings = findUnsafeContent(workflow);
    assert.ok(findings.length > 0);
    assert.ok(findings.every((finding) => /^\$\./.test(finding.path) && /^[A-Z_]+$/.test(finding.code)));
    assert.equal(JSON.stringify(findings).includes(fakeBotToken), false);
    assert.equal(JSON.stringify(findings).includes('pw1234xyz'), false);
  }
});

test('a linha de comando falha sem gravar nada se houver conteúdo inseguro', () => {
  const input = mkdtempSync(join(tmpdir(), 'normalize-in-'));
  const output = join(mkdtempSync(join(tmpdir(), 'normalize-out-')), 'saida');
  writeFileSync(join(input, 'transport-health.json'), JSON.stringify(exported()));
  writeFileSync(
    join(input, 'execute-actions.json'),
    JSON.stringify({ ...exported(), id: 'execute-actions', nodes: [{ name: 'Call', credentials: { a: { id: '1' } } }] }),
  );

  const run = spawnSync('node', [script, 'normalize', input, output], { encoding: 'utf8' });

  assert.notEqual(run.status, 0);
  assert.match(run.stderr, /UNSAFE_CONTENT/);
  assert.match(run.stderr, /execute-actions/);
  assert.equal(existsSync(output) ? readdirSync(output).length : 0, 0);
});

test('a linha de comando normaliza todos os arquivos quando é seguro', () => {
  const input = mkdtempSync(join(tmpdir(), 'normalize-in-'));
  const output = join(mkdtempSync(join(tmpdir(), 'normalize-out-')), 'saida');
  writeFileSync(join(input, 'transport-health.json'), JSON.stringify(exported()));

  const run = spawnSync('node', [script, 'normalize', input, output], { encoding: 'utf8' });

  assert.equal(run.status, 0, run.stderr);
  const written = JSON.parse(readFileSync(join(output, 'transport-health.json'), 'utf8'));
  assert.equal('updatedAt' in written, false);
});

test('compare lista só nomes de workflow e caminhos de campo divergentes', () => {
  const actual = mkdtempSync(join(tmpdir(), 'compare-actual-'));
  const reference = mkdtempSync(join(tmpdir(), 'compare-reference-'));
  mkdirSync(actual, { recursive: true });
  const changed = exported();
  changed.nodes[0].parameters.path = 'secret-looking-path-value';
  writeFileSync(join(actual, 'transport-health.json'), JSON.stringify(changed));
  writeFileSync(join(actual, 'manual-extra.json'), JSON.stringify({ ...exported(), id: 'manual-extra' }));
  writeFileSync(join(reference, 'transport-health.json'), JSON.stringify(exported()));
  writeFileSync(join(reference, 'channel-delivery.json'), JSON.stringify({ ...exported(), id: 'channel-delivery' }));

  const run = spawnSync('node', [script, 'compare', actual, reference], { encoding: 'utf8' });

  assert.equal(run.status, 1);
  assert.match(run.stdout, /transport-health/);
  assert.match(run.stdout, /\$\.nodes\[0\]\.parameters\.path/);
  assert.match(run.stdout, /EXTRA_IN_INSTANCE/);
  assert.match(run.stdout, /MISSING_IN_INSTANCE/);
  assert.equal(run.stdout.includes('secret-looking-path-value'), false);
});

test('compare não acusa diferença quando só os campos voláteis mudam', () => {
  const actual = mkdtempSync(join(tmpdir(), 'compare-actual-'));
  const reference = mkdtempSync(join(tmpdir(), 'compare-reference-'));
  writeFileSync(join(actual, 'transport-health.json'), JSON.stringify(exported()));
  const clean = serialize(normalize(exported()));
  writeFileSync(join(reference, 'transport-health.json'), clean);

  const run = spawnSync('node', [script, 'compare', actual, reference], { encoding: 'utf8' });

  assert.equal(run.status, 0, run.stdout + run.stderr);
});
