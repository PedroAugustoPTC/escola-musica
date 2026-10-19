import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import http from 'node:http';
import net from 'node:net';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { isLiveBody } from './gate.mjs';

const GATE = fileURLToPath(new URL('./gate.mjs', import.meta.url));

test('só um corpo JSON com status UP e HTTP 200 conta como pronto', () => {
  assert.equal(isLiveBody(200, '{"status":"UP"}'), true);
  assert.equal(isLiveBody(200, 'n8n is starting up. Please wait'), false);
  assert.equal(isLiveBody(404, '{"status":"UP"}'), false);
  assert.equal(isLiveBody(200, '{"status":"DOWN"}'), false);
  assert.equal(isLiveBody(200, ''), false);
});

function freePort() {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address();
      server.close(() => resolve(port));
    });
  });
}

function request(port, path = '/webhook/telegram-update') {
  return new Promise((resolve) => {
    const req = http.request({ host: '127.0.0.1', port, path, method: 'POST', timeout: 5000, agent: false }, (response) => {
      let body = '';
      response.on('data', (chunk) => (body += chunk));
      response.on('end', () => resolve({ status: response.statusCode, body }));
    });
    req.on('error', (error) => resolve({ error: error.code }));
    req.on('timeout', () => req.destroy());
    req.end('{}');
  });
}

async function waitFor(condition, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await condition()) {
      return true;
    }
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  return false;
}

// n8n de mentira: escuta logo, responde 200 "starting up" por 1,5 s e só depois registra os receptores.
// POST /webhook/slow demora 800 ms; qualquer outro POST responde 503 "relayed"; SIGTERM grava a hora e sai.
const FAKE_N8N = `import http from 'node:http';
import { writeFileSync } from 'node:fs';
const readyAt = Date.now() + 1500;
http.createServer((req, res) => {
  if (Date.now() < readyAt) { res.writeHead(200); res.end('n8n is starting up. Please wait'); return; }
  if (req.method === 'GET') { res.writeHead(200, {'content-type': 'application/json'}); res.end('{"status":"UP"}'); return; }
  if (req.url.endsWith('/slow')) { setTimeout(() => { res.writeHead(200); res.end('done'); }, 800); return; }
  res.writeHead(503); res.end('relayed');
}).listen(Number(process.env.N8N_PORT), process.env.N8N_LISTEN_ADDRESS);
process.on('SIGTERM', () => { writeFileSync(process.env.TERM_MARK, String(Date.now())); process.exit(0); });
`;

async function startGate() {
  const directory = mkdtempSync(join(tmpdir(), 'gate-'));
  const fake = join(directory, 'fake-n8n.mjs');
  const mark = join(directory, 'term-mark');
  writeFileSync(fake, FAKE_N8N);
  const publicPort = await freePort();
  const innerPort = await freePort();
  const gate = spawn(process.execPath, [GATE], {
    env: {
      ...process.env,
      GATE_PUBLIC_PORT: String(publicPort),
      GATE_INNER_PORT: String(innerPort),
      GATE_N8N_COMMAND: `${process.execPath} ${fake}`,
      GATE_READY_PATHS: 'a/live,b/live',
      TERM_MARK: mark,
    },
    stdio: 'ignore',
  });
  const exited = new Promise((resolve) => gate.on('exit', resolve));
  return { gate, exited, publicPort, mark, cleanup: () => { gate.kill('SIGKILL'); rmSync(directory, { recursive: true, force: true }); } };
}

test('a porta pública fica fechada até o n8n registrar os receptores e depois repassa a requisição', async () => {
  const { publicPort, cleanup } = await startGate();
  try {
    const early = await request(publicPort);
    assert.equal(early.error, 'ECONNREFUSED');
    assert.equal(await waitFor(async () => (await request(publicPort)).status === 503, 15000), true);
    assert.deepEqual(await request(publicPort), { status: 503, body: 'relayed' });
  } finally {
    cleanup();
  }
});

test('na parada, recusa novas requisições, deixa a em curso terminar e só então manda o n8n parar', async () => {
  const { gate, exited, publicPort, mark, cleanup } = await startGate();
  try {
    assert.equal(await waitFor(async () => (await request(publicPort)).status === 503, 15000), true);
    const slow = request(publicPort, '/webhook/slow');
    await new Promise((resolve) => setTimeout(resolve, 200));
    gate.kill('SIGTERM');
    await new Promise((resolve) => setTimeout(resolve, 200));
    const during = await request(publicPort);
    assert.ok(during.error === 'ECONNREFUSED' || during.status === 503, `durante a parada: ${JSON.stringify(during)}`);
    assert.notEqual(during.body, 'relayed');
    const finished = await slow;
    const finishedAt = Date.now();
    assert.deepEqual(finished, { status: 200, body: 'done' });
    assert.equal(await exited, 0);
    assert.ok(Number(readFileSync(mark, 'utf8')) >= finishedAt - 50, 'o n8n só recebe o SIGTERM depois da requisição em curso');
  } finally {
    cleanup();
  }
});

test('se o n8n encerra, a porta de entrada encerra com o mesmo código', async () => {
  const gate = spawn(process.execPath, [GATE], {
    env: { ...process.env, GATE_N8N_COMMAND: `${process.execPath} -e process.exit(7)`, GATE_PUBLIC_PORT: String(await freePort()) },
    stdio: 'ignore',
  });
  assert.equal(await new Promise((resolve) => gate.on('exit', resolve)), 7);
});
