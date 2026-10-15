import type { ReactNode } from 'react';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { vi } from 'vitest';
import { ThemeProvider } from '../src/app/ThemeProvider';

export interface SimulatedResponse {
  status?: number;
  body?: unknown;
  headers?: Record<string, string>;
}

export type SimulatedRoute = (
  url: string,
  init: RequestInit,
) => SimulatedResponse | Promise<SimulatedResponse>;

/**
 * Substitui o fetch global por respostas por método e caminho, ex.:
 * `{ 'GET /api/v1/inicio': { corpo: {...} } }`. Chamadas sem rota devolvem 404.
 */
export function mockApi(routes: Record<string, SimulatedResponse | SimulatedRoute>) {
  document.cookie = 'XSRF-TOKEN=test-token; path=/';
  const calls: { method: string; url: string; init: RequestInit }[] = [];
  const fakeFetch = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = typeof input === 'string' ? input : input.toString();
    const method = (init.method ?? 'GET').toUpperCase();
    calls.push({ method, url, init });
    const path = url.split('?')[0]!;
    const key =
      Object.keys(routes).find((k) => k === `${method} ${url}`) ??
      Object.keys(routes).find((k) => k === `${method} ${path}`);
    const definition = key
      ? routes[key]!
      : {
          status: 404,
          body: { code: 'RESOURCE_NOT_FOUND', message: 'Não encontramos o que você procurou.' },
        };
    const response = typeof definition === 'function' ? await definition(url, init) : definition;
    const status = response.status ?? 200;
    const body = response.body;
    const isBlob = body instanceof Blob;
    return new Response(status === 204 || body === undefined ? null : isBlob ? body : JSON.stringify(body), {
      status,
      headers: {
        'Content-Type': isBlob ? 'application/pdf' : 'application/json',
        ...(response.headers ?? {}),
      },
    });
  });
  vi.stubGlobal('fetch', fakeFetch);
  return { fetch: fakeFetch, calls };
}

export const LIGHT_SESSION = {
  teacher: { id: '0190a000-0000-7000-8000-000000000001', name: 'Clara Souza' },
  sessionExpiresAt: '2026-09-21T23:00:00Z',
  telegramLinked: false,
};

/** Renderiza as rotas dadas com QueryClient, tema e roteador em memória. */
export function renderRoutes(routes: RouteObject[], initialPath: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  const result = render(
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <RouterProvider router={router} />
      </ThemeProvider>
    </QueryClientProvider>,
  );
  return { ...result, router, client };
}

/** Renderiza um componente isolado com os provedores. */
export function renderWithProviders(element: ReactNode, path = '/') {
  return renderRoutes([{ path: '*', element }], path);
}
