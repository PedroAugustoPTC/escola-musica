// Testes do simulador do Telegram (feature 004, R-12). Rodam na imagem do simulador: node --test
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { after, before, test } from 'node:test';
import { createSimulator } from './server.mjs';

const TOKEN = 'sim-token';
let simulator;
let base;

before(async () => {
  simulator = createSimulator({ botToken: TOKEN, retryMs: 50 });
  await simulator.listen(0);
  base = `http://127.0.0.1:${simulator.port()}`;
});

after(() => simulator.close());

async function bot(method, body, token = TOKEN) {
  const response = await fetch(`${base}/bot${token}/${method}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body ?? {}),
  });
  return { status: response.status, json: await response.json() };
}

async function control(path, method = 'GET', body) {
  const response = await fetch(`${base}/_control${path}`, {
    method,
    headers: { 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return response.json();
}

/** Webhook de teste: responde com a sequência de status informada e guarda o que recebeu. */
async function receiver(statuses) {
  const received = [];
  const server = createServer((request, response) => {
    const chunks = [];
    request.on('data', (chunk) => chunks.push(chunk));
    request.on('end', () => {
      received.push({ secret: request.headers['x-telegram-bot-api-secret-token'], body: JSON.parse(Buffer.concat(chunks).toString()) });
      const status = statuses[Math.min(received.length - 1, statuses.length - 1)];
      response.writeHead(status);
      response.end();
    });
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  return { received, url: `http://127.0.0.1:${server.address().port}/hook`, close: () => server.close() };
}

async function until(condition, timeoutMs = 5000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await condition()) return;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error('condição não atendida a tempo');
}

test('registra, consulta e remove o webhook', async () => {
  await control('/reset', 'POST');
  const set = await bot('setWebhook', { url: 'http://alvo/hook', secret_token: 'segredo', allowed_updates: ['message', 'callback_query'], max_connections: 10 });
  assert.equal(set.json.ok, true);

  const info = (await bot('getWebhookInfo')).json.result;
  assert.equal(info.url, 'http://alvo/hook');
  assert.deepEqual(info.allowed_updates, ['message', 'callback_query']);
  assert.equal(info.pending_update_count, 0);

  await bot('deleteWebhook');
  assert.equal((await bot('getWebhookInfo')).json.result.url, '');
});

test('recusa token do bot diferente do configurado', async () => {
  const result = await bot('getMe', {}, 'outro-token');
  assert.equal(result.status, 401);
  assert.equal(result.json.ok, false);
});

test('entrega o update ao webhook com o cabeçalho de segredo', async () => {
  await control('/reset', 'POST');
  const target = await receiver([200]);
  await bot('setWebhook', { url: target.url, secret_token: 'segredo-123' });

  const injected = await control('/updates', 'POST', { chatId: 77, text: 'oi' });

  await until(() => target.received.length === 1);
  assert.equal(target.received[0].secret, 'segredo-123');
  assert.equal(target.received[0].body.update_id, injected.updateId);
  assert.equal(target.received[0].body.message.chat.id, 77);
  assert.equal(target.received[0].body.message.text, 'oi');
  target.close();
});

test('retém e reenvia o update enquanto a resposta não for 2xx', async () => {
  await control('/reset', 'POST');
  const target = await receiver([503, 503, 200]);
  await bot('setWebhook', { url: target.url, secret_token: 's' });

  await control('/updates', 'POST', { chatId: 78, text: 'tente' });

  await until(() => target.received.length === 3);
  assert.deepEqual(new Set(target.received.map((item) => item.body.update_id)).size, 1);
  await until(async () => (await bot('getWebhookInfo')).json.result.pending_update_count === 0);
  target.close();
});

test('mantém o update pendente e mostra o último erro quando não há webhook alcançável', async () => {
  await control('/reset', 'POST');
  await bot('setWebhook', { url: 'http://127.0.0.1:1/hook', secret_token: 's' });
  await control('/updates', 'POST', { chatId: 79, text: 'espera' });

  await until(async () => (await bot('getWebhookInfo')).json.result.last_error_message !== undefined);
  const info = (await bot('getWebhookInfo')).json.result;
  assert.equal(info.pending_update_count, 1);
  assert.ok(info.last_error_message.length > 0);
});

test('guarda a transcrição por chat, na ordem', async () => {
  await control('/reset', 'POST');
  const first = (await bot('sendMessage', { chat_id: 5, text: 'um' })).json.result;
  await bot('sendMessage', { chat_id: 6, text: 'outro chat' });
  await bot('editMessageText', { chat_id: 5, message_id: first.message_id, text: 'um editado' });
  await bot('answerCallbackQuery', { callback_query_id: 'cb1' });

  const transcript = (await control('/transcript?chat=5')).transcript;
  assert.deepEqual(transcript.map((item) => `${item.kind}:${item.text}`), ['message:um', 'edit:um editado']);
  assert.equal((await control('/transcript?chat=6')).transcript.length, 1);
});

test('deleteMessage apaga a mensagem, registra na transcrição e repetir vira "message to delete not found"', async () => {
  await control('/reset', 'POST');
  const sent = (await bot('sendMessage', { chat_id: 21, text: 'a apagar' })).json.result;

  const first = await bot('deleteMessage', { chat_id: 21, message_id: sent.message_id });
  const again = await bot('deleteMessage', { chat_id: 21, message_id: sent.message_id });

  assert.equal(first.status, 200);
  assert.deepEqual(first.json, { ok: true, result: true });
  assert.equal(again.status, 400);
  assert.equal(again.json.ok, false);
  assert.equal(again.json.description, 'Bad Request: message to delete not found');
  const transcript = (await control('/transcript?chat=21')).transcript;
  assert.deepEqual(transcript.map((item) => item.kind), ['message', 'delete']);
  assert.equal(transcript[1].messageId, sent.message_id);
  assert.equal(transcript[1].text, undefined);
});

test('deleteMessage apaga mensagem de usuário injetada e exige message_id', async () => {
  await control('/reset', 'POST');

  const missing = await bot('deleteMessage', { chat_id: 22 });
  const unknown = await bot('deleteMessage', { chat_id: 22, message_id: 99999 });

  assert.equal(missing.status, 400);
  assert.equal(missing.json.description, 'Bad Request: message identifier is not specified');
  assert.equal(unknown.status, 200);
});

test('deleteMessage recusado pela falha programada (MESSAGE_NOT_DELETABLE no transporte)', async () => {
  await control('/reset', 'POST');
  await control('/fail-next', 'POST', { method: 'deleteMessage', status: 400, description: "Bad Request: message can't be deleted" });

  const refused = await bot('deleteMessage', { chat_id: 23, message_id: 5 });
  const worked = await bot('deleteMessage', { chat_id: 23, message_id: 5 });

  assert.equal(refused.status, 400);
  assert.equal(refused.json.description, "Bad Request: message can't be deleted");
  assert.equal(worked.status, 200);
  assert.deepEqual((await control('/transcript?chat=23')).transcript.map((item) => item.kind), ['delete']);
});

test('falha programada vale só para a próxima chamada do método', async () => {
  await control('/reset', 'POST');
  await control('/fail-next', 'POST', { method: 'sendMessage', status: 403, description: 'Forbidden: bot was blocked by the user' });

  const failed = await bot('sendMessage', { chat_id: 9, text: 'a' });
  const worked = await bot('sendMessage', { chat_id: 9, text: 'b' });

  assert.equal(failed.status, 403);
  assert.equal(failed.json.description, 'Forbidden: bot was blocked by the user');
  assert.equal(worked.json.ok, true);
});

test('sendDocument multipart registra o nome do arquivo e o tamanho', async () => {
  await control('/reset', 'POST');
  const form = new FormData();
  form.set('chat_id', '12');
  form.set('caption', 'legenda');
  form.set('document', new Blob([Buffer.alloc(300, 1)], { type: 'application/pdf' }), 'arquivo.pdf');

  const response = await fetch(`${base}/bot${TOKEN}/sendDocument`, { method: 'POST', body: form });

  assert.equal((await response.json()).ok, true);
  const entry = (await control('/transcript?chat=12')).transcript[0];
  assert.equal(entry.kind, 'document');
  assert.equal(entry.fileName, 'arquivo.pdf');
  assert.ok(entry.size >= 300);
});

test('pausar a entrega retém os updates até soltar', async () => {
  await control('/reset', 'POST');
  const target = await receiver([200]);
  await bot('setWebhook', { url: target.url, secret_token: 's' });
  await control('/hold', 'POST', { on: true });
  await control('/updates', 'POST', { chatId: 80, text: 'preso' });
  await new Promise((resolve) => setTimeout(resolve, 200));
  assert.equal(target.received.length, 0);

  await control('/hold', 'POST', { on: false });

  await until(() => target.received.length === 1);
  target.close();
});

test('respeita max_connections: nunca mais entregas em voo do que o limite do webhook', async () => {
  let open = 0;
  let peak = 0;
  const held = [];
  const server = createServer((request, response) => {
    open += 1;
    peak = Math.max(peak, open);
    held.push(response);
    request.resume();
    response.on('close', () => (open -= 1));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    await bot('setWebhook', { url: `http://127.0.0.1:${server.address().port}/hook`, secret_token: 's', max_connections: 2 });
    for (let chatId = 1; chatId <= 6; chatId++) {
      await control('/updates', 'POST', { chatId, text: 'oi' });
    }
    await new Promise((resolve) => setTimeout(resolve, 400));
    assert.equal(peak, 2);
    for (const response of held) {
      response.writeHead(200).end();
    }
  } finally {
    await bot('deleteWebhook');
    server.close();
    await control('/reset', 'POST', {});
  }
});
