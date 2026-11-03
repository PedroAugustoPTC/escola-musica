import type { components } from './api-types';
import type { components as administrationComponents } from './administration-api-types';
import { msg } from '../messages/messages';

/** Tipos do contrato da API (gerados de contracts/api-portal.openapi.yaml). */
export type Schemas = components['schemas'];
/**
 * Corpo de erro do servidor. Vem do contrato da administração, que é o que define `reference` (código da falha
 * interna, em toda resposta de erro); o contrato 001 é histórico e não o declara.
 */
export type ContractError = administrationComponents['schemas']['Error'];

export const BASE_API = '/api/v1';
export const SESSION_EXPIRED_EVENT = 'reports:session-expired';
/** Sessão do administrador expirada (401 em `/admin/**`); independente da sessão do professor. */
export const ADMIN_SESSION_EXPIRED_EVENT = 'reports:admin-session-expired';
/** Senha provisória do administrador: o servidor exige a troca (403 `PASSWORD_CHANGE_REQUIRED`). */
export const ADMIN_PASSWORD_CHANGE_REQUIRED_EVENT = 'reports:admin-password-change-required';
const CODE_PASSWORD_CHANGE_REQUIRED = 'PASSWORD_CHANGE_REQUIRED';
const BLOCKED_CODES = new Set(['OPERATION_TEMPORARILY_BLOCKED', 'TEMPORARILY_BLOCKED']);
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
  /** Código de referência da falha interna (`INTERNAL_ERROR`), para o suporte localizar o registro. */
  readonly reference?: string;

  constructor(status: number, body: Partial<ContractError> | null, retryAfterHeader?: string | null) {
    const code = body?.code ?? (status === 0 ? 'NO_CONNECTION' : 'INTERNAL_ERROR');
    const reference = typeof body?.reference === 'string' && body.reference ? body.reference : undefined;
    super(body?.message ?? msg(code, { reference }));
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.reference = reference;
    this.details = (body?.details as Record<string, unknown> | undefined) ?? {};
    this.retryAfterSeconds = body?.retryAfterSeconds ?? parseSeconds(retryAfterHeader);
  }
}

function parseSeconds(value: string | null | undefined): number | undefined {
  if (!value) return undefined;
  const seconds = Number(value);
  return Number.isFinite(seconds) && seconds >= 0 ? Math.ceil(seconds) : undefined;
}

/**
 * Tempo restante (em segundos) de um bloqueio temporário (`TEMPORARILY_BLOCKED` ou
 * `OPERATION_TEMPORARILY_BLOCKED`), vindo do corpo ou do cabeçalho `Retry-After`.
 * Devolve `null` quando o erro não é de bloqueio.
 */
export function blockedRemainingSeconds(error: unknown): number | null {
  if (!(error instanceof ApiError) || !BLOCKED_CODES.has(error.code)) return null;
  return error.retryAfterSeconds ?? 0;
}

/** Caminho (relativo a `BASE_API`) da área administrativa: `/admin` ou `/admin/...`. */
function isAdminPath(path: string): boolean {
  return /^\/admin(?:[/?#]|$)/.test(path);
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
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  form?: FormData;
  /** Não dispara o evento de sessão expirada (usado na própria tela de entrada, do professor ou do administrador). */
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
      // o cookie `admin_session` (Path=/api/v1/admin) e o `session` seguem só para a própria origem
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
    const error = new ApiError(response.status, errorBody, response.headers.get('Retry-After'));
    const admin = isAdminPath(path);
    if (response.status === 401 && !options.ignore401) {
      const event = admin ? ADMIN_SESSION_EXPIRED_EVENT : SESSION_EXPIRED_EVENT;
      window.dispatchEvent(new CustomEvent(event, { detail: error.code }));
    }
    if (admin && response.status === 403 && error.code === CODE_PASSWORD_CHANGE_REQUIRED) {
      window.dispatchEvent(new CustomEvent(ADMIN_PASSWORD_CHANGE_REQUIRED_EVENT, { detail: error.code }));
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

/** Como `requestBlob`, e devolve também o nome sugerido pelo servidor (Content-Disposition), se houver. */
export async function requestNamedBlob(
  path: string,
  signal?: AbortSignal,
): Promise<{ blob: Blob; fileName: string | null }> {
  const response = await run(path, { signal });
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = /filename="?([^";]+)"?/i.exec(disposition);
  return { blob: await response.blob(), fileName: match ? match[1] : null };
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  postForm: <T>(path: string, form: FormData) => request<T>(path, { method: 'POST', form }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
