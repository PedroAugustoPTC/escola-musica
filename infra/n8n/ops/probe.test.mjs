// Testes do cliente HTTP do n8n (feature 004). Rodam dentro da imagem: node --test /opt/ops
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { createServer } from 'node:http';
import { dirname, join } from 'node:path';
import { after, before, test } from 'node:test';
import { fileURLToPath } from 'node:url';

const script = join(dirname(fileURLToPath(import.meta.url)), 'probe.mjs');

// segredo de teste gerado na hora: não existe literal parecido com segredo neste arquivo
const secret = randomBytes(18).toString('base64url');

let server;
let port;
let received;

before(async () => {
  server = createServer((request, response) => {
    const chunks = [];
    request.on('data', (chunk) => chunks.push(chunk));
    request.on('end', () => {
      received = { method: request.method, url: request.url, headers: request.headers, body: Buffer.concat(chunks).toString() };
      if (request.url.startsWith('/echo-secret')) {
        response.writeHead(200, { 'content-type': 'text/plain', 'x-echo': secret });
        response.end(`valor ${secret} no corpo`);
        return;
      }
      if (request.url.startsWith('/missing')) {
        response.writeHead(404);
        response.end();
        return;
      }
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end('{"status":"UP"}');
    });
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  port = server.address().port;
});

after(() => server.close());

function probe(args, env = {}) {
  return new Promise((resolve) => {
    const child = spawn('node', [script, ...args], { env: { ...process.env, ...env } });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => (stdout += chunk));
    child.stderr.on('data', (chunk) => (stderr += chunk));
    child.on('close', (status) => resolve({ status, stdout, stderr }));
  });
}

test('imprime o status e o corpo', async () => {
  const result = await probe(['GET', `http://127.0.0.1:${port}/ok`]);

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout.split('\n')[0], 'HTTP 200');
  assert.match(result.stdout, /\{"status":"UP"\}/);
});

test('status de erro do servidor sai com zero e é impresso', async () => {
  const result = await probe(['GET', `http://127.0.0.1:${port}/missing`]);

  assert.equal(result.status, 0);
  assert.equal(result.stdout.split('\n')[0], 'HTTP 404');
});

test('repassa cabeçalhos e o segredo vem de variável de ambiente', async () => {
  const result = await probe(
    ['POST', `http://127.0.0.1:${port}/x`, '--header', 'X-Plain=visible', '--header-env', 'X-Service-Token=PROBE_TOKEN', '--data', '{"a":1}'],
    { PROBE_TOKEN: secret },
  );

  assert.equal(result.status, 0, result.stderr);
  assert.equal(received.headers['x-plain'], 'visible');
  assert.equal(received.headers['x-service-token'], secret);
  assert.equal(received.method, 'POST');
  assert.equal(received.body, '{"a":1}');
});

test('o corpo pode vir de variável de ambiente', async () => {
  const result = await probe(['POST', `http://127.0.0.1:${port}/x`, '--data-env', 'PROBE_BODY'], { PROBE_BODY: '{"b":2}' });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(received.body, '{"b":2}');
});

test('substitui {VARIAVEL} na URL pelo valor do ambiente sem imprimir a URL', async () => {
  const result = await probe(['GET', `http://127.0.0.1:${port}/bot{PROBE_TOKEN}/getMe`], { PROBE_TOKEN: secret });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(received.url, `/bot${secret}/getMe`);
  assert.equal((result.stdout + result.stderr).includes(secret), false);
});

test('nunca imprime o valor de um segredo usado, nem se o servidor o devolver', async () => {
  const result = await probe(['GET', `http://127.0.0.1:${port}/echo-secret`, '--header-env', 'X-Service-Token=PROBE_TOKEN'], {
    PROBE_TOKEN: secret,
  });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout.includes(secret), false);
  assert.match(result.stdout, /HTTP 200/);
});

test('falha de conexão sai com código diferente de zero e sem URL na saída', async () => {
  const result = await probe(['GET', `http://127.0.0.1:1/bot{PROBE_TOKEN}/getMe`], { PROBE_TOKEN: secret });

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /CONNECTION_FAILED/);
  assert.equal((result.stdout + result.stderr).includes(secret), false);
  assert.equal((result.stdout + result.stderr).includes('127.0.0.1'), false);
});

test('variável de ambiente ausente é erro de uso', async () => {
  const result = await probe(['GET', `http://127.0.0.1:${port}/x`, '--header-env', 'X-Token=DOES_NOT_EXIST_VARIABLE']);

  assert.equal(result.status, 2);
  assert.match(result.stderr, /MISSING_ENV/);
});
