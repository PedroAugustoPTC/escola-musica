// Porta de entrada do n8n (feature 004, R-13a): sobe o n8n numa porta interna, só abre a porta pública (5678) depois que
// os receptores de webhook estão registrados e, na parada, deixa de aceitar requisições e espera as em curso terminarem
// antes de mandar o n8n parar.
//
// Por quê: (1) logo depois de escutar, o n8n responde 200 "n8n is starting up. Please wait" a qualquer requisição, mesmo
// de POST em /webhook/*; (2) na parada o executor de Code encerra primeiro e as execuções penduram. O Telegram entende
// 200 como "entregue" e nunca reenvia: o update some. Com a porta fechada nessas janelas, o Telegram recebe "conexão
// recusada" ou 503, reenvia, e o backend deduplica pelo update_id.
//
// É só infraestrutura de transporte: repassa requisições HTTP ao n8n, sem ler nem decidir nada sobre o conteúdo.
import { spawn } from 'node:child_process';
import http from 'node:http';
import net from 'node:net';

const PUBLIC_PORT = Number(process.env.GATE_PUBLIC_PORT ?? 5678);
const INNER_PORT = Number(process.env.GATE_INNER_PORT ?? 5680);
const READY_PATHS = (process.env.GATE_READY_PATHS ?? 'transport-health/live,telegram-inbound/live,channel-delivery/live').split(',');
const COMMAND = (process.env.GATE_N8N_COMMAND ?? 'n8n start').split(' ');
const DRAIN_LIMIT_MS = Number(process.env.GATE_DRAIN_LIMIT_MS ?? 20000);
const POLL_MS = 100;

export function isLiveBody(status, body) {
  if (status !== 200) {
    return false;
  }
  try {
    return JSON.parse(body).status === 'UP';
  } catch {
    return false;
  }
}

function probe(path) {
  return new Promise((resolve) => {
    const request = http.get({ host: '127.0.0.1', port: INNER_PORT, path: `/webhook/${path}`, timeout: 2000 }, (response) => {
      let body = '';
      response.on('data', (chunk) => (body += chunk));
      response.on('end', () => resolve(isLiveBody(response.statusCode, body)));
    });
    request.on('error', () => resolve(false));
    request.on('timeout', () => {
      request.destroy();
      resolve(false);
    });
  });
}

async function allLive() {
  const results = await Promise.all(READY_PATHS.map(probe));
  return results.every(Boolean);
}

function createGate() {
  const state = { draining: false, inflight: 0, onIdle: null };

  function finished() {
    state.inflight -= 1;
    if (state.draining) {
      server.closeIdleConnections();
      if (state.inflight === 0 && state.onIdle) {
        state.onIdle();
      }
    }
  }

  const server = http.createServer((request, response) => {
    if (state.draining) {
      response.writeHead(503, { connection: 'close' });
      response.end();
      return;
    }
    state.inflight += 1;
    let done = false;
    const finish = () => {
      if (!done) {
        done = true;
        finished();
      }
    };
    response.on('close', finish);
    const upstream = http.request(
      { host: '127.0.0.1', port: INNER_PORT, method: request.method, path: request.url, headers: request.headers },
      (upstreamResponse) => {
        response.writeHead(upstreamResponse.statusCode, upstreamResponse.statusMessage, upstreamResponse.headers);
        upstreamResponse.pipe(response);
      },
    );
    upstream.on('error', () => {
      if (!response.headersSent) {
        response.writeHead(502, { connection: 'close' });
      }
      response.end();
    });
    request.on('aborted', () => upstream.destroy());
    request.pipe(upstream);
  });

  // editor local (websocket/SSE): túnel de bytes, só existe no ambiente de desenvolvimento
  server.on('upgrade', (request, socket, head) => {
    if (state.draining) {
      socket.destroy();
      return;
    }
    const upstream = net.connect(INNER_PORT, '127.0.0.1', () => {
      upstream.write(`${request.method} ${request.url} HTTP/1.1\r\n`);
      for (let i = 0; i < request.rawHeaders.length; i += 2) {
        upstream.write(`${request.rawHeaders[i]}: ${request.rawHeaders[i + 1]}\r\n`);
      }
      upstream.write('\r\n');
      upstream.write(head);
      socket.pipe(upstream);
      upstream.pipe(socket);
    });
    const close = () => {
      socket.destroy();
      upstream.destroy();
    };
    socket.on('error', close);
    upstream.on('error', close);
    socket.on('close', close);
    upstream.on('close', close);
  });

  return {
    listen: () => server.listen(PUBLIC_PORT, '::'),
    // para de aceitar e espera o que está em curso (até o limite); depois devolve o controle
    drain: () =>
      new Promise((resolve) => {
        state.draining = true;
        server.close();
        server.closeIdleConnections();
        const timer = setTimeout(resolve, DRAIN_LIMIT_MS);
        state.onIdle = () => {
          clearTimeout(timer);
          resolve();
        };
        if (state.inflight === 0) {
          state.onIdle();
        }
      }),
  };
}

function main() {
  const child = spawn(COMMAND[0], COMMAND.slice(1), {
    stdio: 'inherit',
    env: { ...process.env, N8N_PORT: String(INNER_PORT), N8N_LISTEN_ADDRESS: '127.0.0.1' },
  });
  const gate = createGate();
  let listening = false;
  let stopping = false;

  for (const signal of ['SIGTERM', 'SIGINT']) {
    process.on(signal, async () => {
      if (stopping) {
        return;
      }
      stopping = true;
      if (listening) {
        await gate.drain();
      }
      child.kill(signal);
    });
  }
  child.on('exit', (code, signal) => process.exit(code ?? (signal ? 1 : 0)));

  let checking = false;
  const timer = setInterval(async () => {
    if (checking || listening || stopping) {
      return;
    }
    checking = true;
    try {
      if (await allLive()) {
        listening = true;
        clearInterval(timer);
        gate.listen();
        console.log('n8n: porta aberta; os receptores de webhook estão registrados.');
      }
    } finally {
      checking = false;
    }
  }, POLL_MS);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}
