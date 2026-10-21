import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ADMIN_PASSWORD_CHANGE_REQUIRED_EVENT,
  ADMIN_SESSION_EXPIRED_EVENT,
  ApiError,
  SESSION_EXPIRED_EVENT,
  api,
  blockedRemainingSeconds,
  request,
} from '../../src/services/apiClient';

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

function listen(name: string) {
  const handler = vi.fn();
  window.addEventListener(name, handler);
  return { handler, stop: () => window.removeEventListener(name, handler) };
}

describe('apiClient — sessão do administrador', () => {
  let teacherEvents: ReturnType<typeof listen>;
  let adminEvents: ReturnType<typeof listen>;
  let passwordEvents: ReturnType<typeof listen>;

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/';
    teacherEvents = listen(SESSION_EXPIRED_EVENT);
    adminEvents = listen(ADMIN_SESSION_EXPIRED_EVENT);
    passwordEvents = listen(ADMIN_PASSWORD_CHANGE_REQUIRED_EVENT);
  });

  afterEach(() => {
    teacherEvents.stop();
    adminEvents.stop();
    passwordEvents.stop();
    vi.unstubAllGlobals();
  });

  it('401 em /admin/** dispara só o evento do administrador', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(401, { code: 'SESSION_EXPIRED', message: 'x' })));

    await expect(api.get('/admin/teachers')).rejects.toBeInstanceOf(ApiError);

    expect(adminEvents.handler).toHaveBeenCalledTimes(1);
    expect((adminEvents.handler.mock.calls[0]![0] as CustomEvent<string>).detail).toBe('SESSION_EXPIRED');
    expect(teacherEvents.handler).not.toHaveBeenCalled();
  });

  it('401 fora de /admin/** dispara só o evento do professor', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(401, { code: 'SESSION_EXPIRED', message: 'x' })));

    await expect(api.get('/my-students')).rejects.toBeInstanceOf(ApiError);

    expect(teacherEvents.handler).toHaveBeenCalledTimes(1);
    expect(adminEvents.handler).not.toHaveBeenCalled();
  });

  it('o caminho /administration-x não é tratado como administrativo', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(401, { code: 'SESSION_EXPIRED', message: 'x' })));

    await expect(api.get('/administration-x')).rejects.toBeInstanceOf(ApiError);

    expect(teacherEvents.handler).toHaveBeenCalledTimes(1);
    expect(adminEvents.handler).not.toHaveBeenCalled();
  });

  it('ignore401 em /admin/session (tela de entrada) não dispara evento algum', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(401, { code: 'INVALID_CREDENTIALS', message: 'x' })));

    await expect(
      request('/admin/session', { method: 'POST', body: { email: 'a@b.c', password: 'x' }, ignore401: true }),
    ).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' });

    expect(adminEvents.handler).not.toHaveBeenCalled();
    expect(teacherEvents.handler).not.toHaveBeenCalled();
  });

  it('403 PASSWORD_CHANGE_REQUIRED em /admin/** dispara o evento de troca de senha e não o de sessão', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(403, { code: 'PASSWORD_CHANGE_REQUIRED', message: 'x' })),
    );

    await expect(api.get('/admin/teachers')).rejects.toMatchObject({
      status: 403,
      code: 'PASSWORD_CHANGE_REQUIRED',
    });

    expect(passwordEvents.handler).toHaveBeenCalledTimes(1);
    expect(adminEvents.handler).not.toHaveBeenCalled();
    expect(teacherEvents.handler).not.toHaveBeenCalled();
  });

  it('403 de outro código não dispara o evento de troca de senha', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(403, { code: 'FORBIDDEN', message: 'x' })));

    await expect(api.get('/admin/teachers')).rejects.toBeInstanceOf(ApiError);

    expect(passwordEvents.handler).not.toHaveBeenCalled();
  });

  it('OPERATION_TEMPORARILY_BLOCKED expõe o tempo restante do corpo', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(429, { code: 'OPERATION_TEMPORARILY_BLOCKED', message: 'x', retryAfterSeconds: 90 }),
      ),
    );

    const error = await api.post('/my-students', {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(blockedRemainingSeconds(error)).toBe(90);
  });

  it('usa o cabeçalho Retry-After quando o corpo não traz o tempo', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(429, { code: 'TEMPORARILY_BLOCKED', message: 'x' }, { 'Retry-After': '45' }),
      ),
    );

    const error = await api.post('/admin/session', {}).catch((e: unknown) => e);

    expect(blockedRemainingSeconds(error)).toBe(45);
  });

  it('blockedRemainingSeconds devolve null para erro que não é de bloqueio', () => {
    expect(blockedRemainingSeconds(new ApiError(404, { code: 'RESOURCE_NOT_FOUND', message: 'x' }))).toBeNull();
    expect(blockedRemainingSeconds(new Error('generic failure'))).toBeNull();
  });

  it('envia o cookie da própria origem e o CSRF em PUT e PATCH', async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await api.put('/admin/password', { newPassword: 'x' });
    await api.patch('/admin/teachers/1', { name: 'y' });

    const calls = fetchMock.mock.calls as unknown as [string, RequestInit][];
    expect(calls).toHaveLength(2);
    expect(calls[0]![0]).toBe('/api/v1/admin/password');
    expect(calls[0]![1].method).toBe('PUT');
    expect(calls[1]![1].method).toBe('PATCH');
    for (const [, init] of calls) {
      expect(init.credentials).toBe('same-origin');
      expect((init.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('test-token');
    }
  });
});
