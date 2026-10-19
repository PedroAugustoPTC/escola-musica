// Simulador do Telegram para os ensaios (feature 004, R-12). SÓ para testes: faz o papel do servidor local da API de
// bots (registro de webhook, envio e edição de mensagem, documento, resposta de toque) e o papel do Telegram na
// nuvem (entrega o update ao webhook com o cabeçalho de segredo e RETÉM e REENVIA enquanto a resposta não for 2xx).
// Sem dependências. API de controle em /_control/* (nunca exposta fora da rede de teste).
import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';

export function createSimulator({ botToken = '', retryMs = 1000, deliveryTimeoutMs = 35000 } = {}) {
  const state = freshState();
  let server;
  let timer;

  function freshState() {
    return {
      webhook: { url: '', secret: '', allowed: [], max: 40 },
      queue: [],
      transcripts: new Map(),
      callbacks: [],
      nextUpdate: 1000,
      nextMessage: 1,
      failNext: [],
      hold: false,
      lastError: null,
      lastErrorDate: 0,
      delivered: 0,
      history: new Map(),
    };
  }

  function transcript(chat) {
    const key = String(chat);
    if (!state.transcripts.has(key)) {
      state.transcripts.set(key, []);
    }
    return state.transcripts.get(key);
  }

  // ------------------------------------------------------------------ entrega de updates (papel do Telegram)

  function remember(item, outcome) {
    const key = String(item.update.update_id);
    if (!state.history.has(key)) {
      state.history.set(key, { chat: (item.update.message ?? item.update.callback_query.message).chat.id, attempts: [] });
    }
    state.history.get(key).attempts.push({ at: new Date().toISOString().slice(11, 23), outcome });
  }

  async function deliver(item) {
    item.inflight = true;
    try {
      const response = await fetch(state.webhook.url, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'x-telegram-bot-api-secret-token': state.webhook.secret },
        body: JSON.stringify(item.update),
        signal: AbortSignal.timeout(deliveryTimeoutMs),
      });
      if (response.status >= 200 && response.status < 300) {
        state.queue = state.queue.filter((candidate) => candidate !== item);
        state.delivered += 1;
        state.lastError = null;
        remember(item, response.status);
        return;
      }
      state.lastError = `Wrong response from the webhook: ${response.status}`;
      remember(item, response.status);
    } catch (error) {
      state.lastError = 'Connection error with the webhook';
      remember(item, error?.name === 'TimeoutError' ? 'timeout' : 'conexão');
    }
    state.lastErrorDate = Math.floor(Date.now() / 1000);
    // como o Telegram, o intervalo entre tentativas do mesmo update cresce (até 10x o inicial)
    item.failures = (item.failures ?? 0) + 1;
    item.nextAt = Date.now() + Math.min(retryMs * 2 ** (item.failures - 1), retryMs * 10);
    item.inflight = false;
  }

  function tick() {
    if (state.hold || !state.webhook.url) {
      return;
    }
    const now = Date.now();
    // como o Telegram, nunca mais que max_connections entregas em voo ao mesmo tempo
    let inflight = state.queue.filter((item) => item.inflight).length;
    for (const item of state.queue) {
      if (inflight >= state.webhook.max) {
        break;
      }
      if (!item.inflight && item.nextAt <= now) {
        deliver(item);
        inflight += 1;
      }
    }
  }

  // ------------------------------------------------------------------ API de bots

  async function readBody(request) {
    const chunks = [];
    for await (const chunk of request) {
      chunks.push(chunk);
    }
    return Buffer.concat(chunks);
  }

  async function parse(request, raw) {
    const type = request.headers['content-type'] ?? '';
    if (type.startsWith('multipart/form-data')) {
      const form = await new Response(raw, { headers: { 'content-type': type } }).formData();
      const fields = {};
      for (const [name, value] of form.entries()) {
        fields[name] = typeof value === 'string' ? value : { fileName: value.name, size: value.size };
      }
      return fields;
    }
    if (raw.length === 0) {
      return {};
    }
    try {
      return JSON.parse(raw.toString('utf8'));
    } catch {
      return {};
    }
  }

  function reply(response, status, body) {
    response.writeHead(status, { 'content-type': 'application/json' });
    response.end(JSON.stringify(body));
  }

  function failure(method) {
    const index = state.failNext.findIndex((item) => item.method === method);
    return index < 0 ? null : state.failNext.splice(index, 1)[0];
  }

  function handleBot(method, body, response) {
    const programmed = failure(method);
    if (programmed) {
      reply(response, programmed.status, { ok: false, error_code: programmed.status, description: programmed.description ?? 'Bad Request' });
      return;
    }
    switch (method) {
      case 'setWebhook':
        state.webhook = { url: body.url ?? '', secret: body.secret_token ?? '', allowed: body.allowed_updates ?? [], max: body.max_connections ?? 40 };
        reply(response, 200, { ok: true, result: true, description: 'Webhook was set' });
        return;
      case 'deleteWebhook':
        state.webhook = { url: '', secret: '', allowed: [], max: 40 };
        reply(response, 200, { ok: true, result: true });
        return;
      case 'getWebhookInfo': {
        const result = { url: state.webhook.url, has_custom_certificate: false, pending_update_count: state.queue.length, max_connections: state.webhook.max, allowed_updates: state.webhook.allowed };
        if (state.lastError) {
          result.last_error_date = state.lastErrorDate;
          result.last_error_message = state.lastError;
        }
        reply(response, 200, { ok: true, result });
        return;
      }
      case 'getMe':
        reply(response, 200, { ok: true, result: { id: 1, is_bot: true, first_name: 'Simulador', username: 'simulador_bot' } });
        return;
      case 'setMyCommands':
        reply(response, 200, { ok: true, result: true });
        return;
      case 'sendMessage': {
        const messageId = state.nextMessage++;
        transcript(body.chat_id).push({ kind: 'message', messageId, text: body.text, keyboard: body.reply_markup ?? null });
        reply(response, 200, { ok: true, result: { message_id: messageId, chat: { id: body.chat_id }, text: body.text } });
        return;
      }
      case 'editMessageText':
        transcript(body.chat_id).push({ kind: 'edit', messageId: body.message_id, text: body.text, keyboard: body.reply_markup ?? null });
        reply(response, 200, { ok: true, result: { message_id: body.message_id, chat: { id: body.chat_id }, text: body.text } });
        return;
      case 'sendDocument': {
        const messageId = state.nextMessage++;
        const file = body.document ?? {};
        transcript(body.chat_id).push({ kind: 'document', messageId, fileName: file.fileName, size: file.size, caption: body.caption ?? null });
        reply(response, 200, { ok: true, result: { message_id: messageId, chat: { id: Number(body.chat_id) } } });
        return;
      }
      case 'answerCallbackQuery':
        state.callbacks.push(body.callback_query_id);
        reply(response, 200, { ok: true, result: true });
        return;
      default:
        reply(response, 404, { ok: false, error_code: 404, description: 'Not Found' });
    }
  }

  // ------------------------------------------------------------------ API de controle

  function handleControl(path, query, method, body, response) {
    if (path === '/reset' && method === 'POST') {
      const kept = state.nextUpdate;
      Object.assign(state, freshState(), { nextUpdate: kept });
      reply(response, 200, { ok: true });
    } else if (path === '/updates' && method === 'POST') {
      const updateId = state.nextUpdate++;
      const chatId = Number(body.chatId);
      const from = { id: chatId, is_bot: false, first_name: 'Simulado' };
      const chat = { id: chatId, type: 'private' };
      const update = body.callbackData
        ? { update_id: updateId, callback_query: { id: `cb${updateId}`, data: body.callbackData, from, message: { message_id: 55, date: 1, chat } } }
        : { update_id: updateId, message: { message_id: state.nextMessage++, date: Math.floor(Date.now() / 1000), from, chat, text: body.text } };
      state.queue.push({ update, nextAt: 0, inflight: false });
      reply(response, 200, { ok: true, updateId });
    } else if (path === '/transcript' && method === 'GET') {
      reply(response, 200, { transcript: [...(state.transcripts.get(String(query.get('chat'))) ?? [])] });
    } else if (path === '/history' && method === 'GET') {
      const chat = query.get('chat');
      const entries = [...state.history.entries()].filter(([, value]) => chat === null || String(value.chat) === chat);
      reply(response, 200, { updates: entries.map(([updateId, value]) => ({ updateId, chat: value.chat, attempts: value.attempts })) });
    } else if (path === '/status' && method === 'GET') {
      reply(response, 200, { delivered: state.delivered, pending: state.queue.length, webhookUrl: state.webhook.url, hold: state.hold, callbacks: state.callbacks.length });
    } else if (path === '/fail-next' && method === 'POST') {
      state.failNext.push({ method: body.method, status: body.status ?? 400, description: body.description });
      reply(response, 200, { ok: true });
    } else if (path === '/hold' && method === 'POST') {
      state.hold = body.on === true;
      reply(response, 200, { ok: true, hold: state.hold });
    } else {
      reply(response, 404, { ok: false });
    }
  }

  async function handle(request, response) {
    const url = new URL(request.url, 'http://simulador');
    const raw = await readBody(request);
    if (url.pathname.startsWith('/_control/')) {
      handleControl(url.pathname.slice('/_control'.length), url.searchParams, request.method, await parse(request, raw), response);
      return;
    }
    const match = url.pathname.match(/^\/bot([^/]+)\/(\w+)$/);
    if (!match) {
      reply(response, 404, { ok: false, error_code: 404, description: 'Not Found' });
      return;
    }
    if (botToken && match[1] !== botToken) {
      reply(response, 401, { ok: false, error_code: 401, description: 'Unauthorized' });
      return;
    }
    handleBot(match[2], await parse(request, raw), response);
  }

  return {
    listen(port) {
      server = createServer((request, response) => {
        handle(request, response).catch(() => reply(response, 500, { ok: false, description: 'Internal Server Error' }));
      });
      timer = setInterval(tick, 20);
      return new Promise((resolve) => server.listen(port, '0.0.0.0', resolve));
    },
    port: () => server.address().port,
    close() {
      clearInterval(timer);
      server.closeAllConnections?.();
      server.close();
    },
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const simulator = createSimulator({ botToken: process.env.SIMULATOR_BOT_TOKEN ?? '', retryMs: Number(process.env.SIMULATOR_RETRY_MS ?? 1000) });
  await simulator.listen(Number(process.env.PORT ?? 8081));
  console.log(`simulador do Telegram ouvindo na porta ${simulator.port()}`);
}
