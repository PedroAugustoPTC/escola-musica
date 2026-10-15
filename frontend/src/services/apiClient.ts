import type { components } from './api-types';
import { msg } from '../messages/messages';

/** Tipos do contrato da API (gerados de contracts/api-portal.openapi.yaml). */
export type Schemas = components['schemas'];
export type ContractError = Schemas['Error'];

export const BASE_API = '/api/v1';
export const SESSION_EXPIRED_EVENT = 'reports:session-expired';
const COOKIE_CSRF = 'XSRF-TOKEN';
const HEADER_CSRF = 'X-CSRF-TOKEN';

/**
 * Erro tipado de uma chamada à API. `code` é o código estável do catálogo; `message` é o texto
 * do catálogo (nunca detalhe interno). `status` 0 indica falha de rede (SEM_CONEXAO).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: Record<string, unknown>;
  readonly retryAfterSeconds?: number;

  constructor(status: number, body: Partial<ContractError> | null) {
    const code = body?.code ?? (status === 0 ? 'NO_CONNECTION' : 'INTERNAL_ERROR');
    super(body?.message ?? msg(code));
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = (body?.details as Record<string, unknown> | undefined) ?? {};
    this.retryAfterSeconds = body?.retryAfterSeconds;
  }
}

function readCookie(name: string): string | null {
  const target = `${name}=`;
  for (const part of document.cookie.split(';')) {
    const cookie = part.trim();
    if (cookie.startsWith(target)) {
      return decodeURIComponent(cookie.slice(target.length));
    }
  }
  return null;
}

/** Garante o cookie de CSRF antes da primeira requisição que altera estado. */
async function ensureCsrf(): Promise<string | null> {
  let token = readCookie(COOKIE_CSRF);
  if (!token) {
    try {
      await fetch(`${BASE_API}/session`, { credentials: 'same-origin' });
    } catch {
      // a requisição principal vai relatar a falha de rede
    }
    token = readCookie(COOKIE_CSRF);
  }
  return token;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'DELETE';
  body?: unknown;
  form?: FormData;
  /** Não dispara o evento global de sessão expirada (usado na própria tela de entrada). */
  ignore401?: boolean;
  signal?: AbortSignal;
}

async function run(path: string, options: RequestOptions): Promise<Response> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  let body: BodyInit | undefined;
  if (options.form) {
    body = options.form;
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.body);
  }
  if (method !== 'GET') {
    const token = await ensureCsrf();
    if (token) headers[HEADER_CSRF] = token;
  }
  let response: Response;
  try {
    response = await fetch(`${BASE_API}${path}`, {
      method,
      headers,
      body,
      credentials: 'same-origin',
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError(0, null);
  }
  if (!response.ok) {
    let errorBody: Partial<ContractError> | null;
    try {
      errorBody = (await response.json()) as Partial<ContractError>;
    } catch {
      errorBody = null;
    }
    const error = new ApiError(response.status, errorBody);
    if (response.status === 401 && !options.ignore401) {
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT, { detail: error.code }));
    }
    throw error;
  }
  return response;
}

/** Chamada JSON; devolve `undefined` em 204. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await run(path, options);
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }
  const type = response.headers.get('Content-Type') ?? '';
  if (!type.includes('json')) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** Baixa um recurso binário autenticado (PDF) como Blob. */
export async function requestBlob(path: string, signal?: AbortSignal): Promise<Blob> {
  const response = await run(path, { signal });
  return response.blob();
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  postForm: <T>(path: string, form: FormData) => request<T>(path, { method: 'POST', form }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
