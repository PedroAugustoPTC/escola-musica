import type { components } from './api-tipos';
import { msg } from '../mensagens/mensagens';

/** Tipos do contrato da API (gerados de contracts/api-portal.openapi.yaml). */
export type Esquemas = components['schemas'];
export type ErroContrato = Esquemas['Error'];

export const BASE_API = '/api/v1';
export const EVENTO_SESSAO_EXPIRADA = 'relatorios:sessao-expirada';
const COOKIE_CSRF = 'XSRF-TOKEN';
const CABECALHO_CSRF = 'X-CSRF-TOKEN';

/**
 * Erro tipado de uma chamada à API. `codigo` é o código estável do catálogo; `mensagem` é o texto
 * do catálogo (nunca detalhe interno). `status` 0 indica falha de rede (SEM_CONEXAO).
 */
export class ErroApi extends Error {
  readonly status: number;
  readonly codigo: string;
  readonly detalhes: Record<string, unknown>;
  readonly retryAfterSeconds?: number;

  constructor(status: number, corpo: Partial<ErroContrato> | null) {
    const codigo = corpo?.code ?? (status === 0 ? 'NO_CONNECTION' : 'INTERNAL_ERROR');
    super(corpo?.message ?? msg(codigo));
    this.name = 'ErroApi';
    this.status = status;
    this.codigo = codigo;
    this.detalhes = (corpo?.details as Record<string, unknown> | undefined) ?? {};
    this.retryAfterSeconds = corpo?.retryAfterSeconds;
  }
}

function lerCookie(nome: string): string | null {
  const alvo = `${nome}=`;
  for (const parte of document.cookie.split(';')) {
    const cookie = parte.trim();
    if (cookie.startsWith(alvo)) {
      return decodeURIComponent(cookie.slice(alvo.length));
    }
  }
  return null;
}

/** Garante o cookie de CSRF antes da primeira requisição que altera estado. */
async function garantirCsrf(): Promise<string | null> {
  let token = lerCookie(COOKIE_CSRF);
  if (!token) {
    try {
      await fetch(`${BASE_API}/session`, { credentials: 'same-origin' });
    } catch {
      // a requisição principal vai relatar a falha de rede
    }
    token = lerCookie(COOKIE_CSRF);
  }
  return token;
}

export interface OpcoesRequisicao {
  metodo?: 'GET' | 'POST' | 'DELETE';
  corpo?: unknown;
  formulario?: FormData;
  /** Não dispara o evento global de sessão expirada (usado na própria tela de entrada). */
  ignorar401?: boolean;
  sinal?: AbortSignal;
}

async function executar(caminho: string, opcoes: OpcoesRequisicao): Promise<Response> {
  const metodo = opcoes.metodo ?? 'GET';
  const cabecalhos: Record<string, string> = { Accept: 'application/json' };
  let corpo: BodyInit | undefined;
  if (opcoes.formulario) {
    corpo = opcoes.formulario;
  } else if (opcoes.corpo !== undefined) {
    cabecalhos['Content-Type'] = 'application/json';
    corpo = JSON.stringify(opcoes.corpo);
  }
  if (metodo !== 'GET') {
    const token = await garantirCsrf();
    if (token) cabecalhos[CABECALHO_CSRF] = token;
  }
  let resposta: Response;
  try {
    resposta = await fetch(`${BASE_API}${caminho}`, {
      method: metodo,
      headers: cabecalhos,
      body: corpo,
      credentials: 'same-origin',
      signal: opcoes.sinal,
    });
  } catch (erro) {
    if (erro instanceof DOMException && erro.name === 'AbortError') throw erro;
    throw new ErroApi(0, null);
  }
  if (!resposta.ok) {
    let corpoErro: Partial<ErroContrato> | null;
    try {
      corpoErro = (await resposta.json()) as Partial<ErroContrato>;
    } catch {
      corpoErro = null;
    }
    const erro = new ErroApi(resposta.status, corpoErro);
    if (resposta.status === 401 && !opcoes.ignorar401) {
      window.dispatchEvent(new CustomEvent(EVENTO_SESSAO_EXPIRADA, { detail: erro.codigo }));
    }
    throw erro;
  }
  return resposta;
}

/** Chamada JSON; devolve `undefined` em 204. */
export async function requisitar<T>(caminho: string, opcoes: OpcoesRequisicao = {}): Promise<T> {
  const resposta = await executar(caminho, opcoes);
  if (resposta.status === 204 || resposta.headers.get('Content-Length') === '0') {
    return undefined as T;
  }
  const tipo = resposta.headers.get('Content-Type') ?? '';
  if (!tipo.includes('json')) {
    return undefined as T;
  }
  return (await resposta.json()) as T;
}

/** Baixa um recurso binário autenticado (PDF) como Blob. */
export async function requisitarBlob(caminho: string, sinal?: AbortSignal): Promise<Blob> {
  const resposta = await executar(caminho, { sinal });
  return resposta.blob();
}

export const api = {
  get: <T>(caminho: string, sinal?: AbortSignal) => requisitar<T>(caminho, { sinal }),
  post: <T>(caminho: string, corpo?: unknown) => requisitar<T>(caminho, { metodo: 'POST', corpo }),
  postFormulario: <T>(caminho: string, formulario: FormData) =>
    requisitar<T>(caminho, { metodo: 'POST', formulario }),
  delete: <T>(caminho: string) => requisitar<T>(caminho, { metodo: 'DELETE' }),
};
