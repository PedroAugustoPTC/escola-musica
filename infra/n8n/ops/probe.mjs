// Cliente HTTP mínimo que roda DENTRO do contêiner do n8n (feature 004): os scripts do host só precisam de Docker.
//
//   node probe.mjs <METODO> <url> [--header Nome=valor] [--header-env Nome=VARIAVEL]
//                  [--data texto | --data-env VARIAVEL] [--timeout ms]
//
// - segredos só por variável de ambiente (--header-env, --data-env e {VARIAVEL} dentro da URL), nunca por argumento;
// - imprime `HTTP <status>` e o corpo; qualquer valor de segredo usado é trocado por *** na saída;
// - falha de conexão: código 3, sem imprimir URL; uso incorreto ou variável ausente: código 2.
import { pathToFileURL } from 'node:url';

function fail(code, label, message) {
  console.error(`[${label}] ${message}`);
  return code;
}

function readEnv(name, used) {
  const value = process.env[name];
  if (value === undefined || value === '') {
    throw Object.assign(new Error(`variável de ambiente ${name} ausente`), { label: 'MISSING_ENV' });
  }
  used.push(value);
  return value;
}

function redact(text, secrets) {
  return secrets.reduce((current, secret) => current.split(secret).join('***'), text);
}

export async function run(argv) {
  const [method, rawUrl, ...options] = argv;
  if (!method || !rawUrl) {
    return fail(2, 'USAGE', 'Uso: probe.mjs <METODO> <url> [--header N=v] [--header-env N=VAR] [--data t | --data-env VAR] [--timeout ms]');
  }
  const secrets = [];
  const headers = {};
  let body;
  let timeout = 10000;
  try {
    for (let index = 0; index < options.length; index += 1) {
      const option = options[index];
      const value = options[index + 1];
      if (option === '--header' || option === '--header-env') {
        const separator = value.indexOf('=');
        const name = value.slice(0, separator);
        const rest = value.slice(separator + 1);
        headers[name] = option === '--header' ? rest : readEnv(rest, secrets);
        index += 1;
      } else if (option === '--data') {
        body = value;
        index += 1;
      } else if (option === '--data-env') {
        body = readEnv(value, []);
        index += 1;
      } else if (option === '--timeout') {
        timeout = Number(value);
        index += 1;
      } else {
        return fail(2, 'USAGE', `opção desconhecida: ${option}`);
      }
    }
    const url = rawUrl.replace(/\{([A-Z0-9_]+)\}/g, (_, name) => readEnv(name, secrets));
    const response = await fetch(url, {
      method,
      headers,
      body,
      redirect: 'manual',
      signal: AbortSignal.timeout(timeout),
    });
    const text = await response.text();
    console.log(`HTTP ${response.status}`);
    if (text.length > 0) {
      console.log(redact(text, secrets));
    }
    return 0;
  } catch (error) {
    if (error && error.label === 'MISSING_ENV') {
      return fail(2, 'MISSING_ENV', error.message);
    }
    // a mensagem de falha de conexão pode trazer a URL (com o token do bot): nunca é impressa
    return fail(3, 'CONNECTION_FAILED', 'não foi possível concluir a chamada HTTP');
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = await run(process.argv.slice(2));
}
