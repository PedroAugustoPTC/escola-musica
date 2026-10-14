import type { ReactNode } from 'react';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { vi } from 'vitest';
import { TemaProvedor } from '../src/app/TemaProvedor';

export interface RespostaSimulada {
  status?: number;
  corpo?: unknown;
  cabecalhos?: Record<string, string>;
}

export type RotaSimulada = (url: string, init: RequestInit) => RespostaSimulada | Promise<RespostaSimulada>;

/**
 * Substitui o fetch global por respostas por método e caminho, ex.:
 * `{ 'GET /api/v1/inicio': { corpo: {...} } }`. Chamadas sem rota devolvem 404.
 */
export function simularApi(rotas: Record<string, RespostaSimulada | RotaSimulada>) {
  document.cookie = 'XSRF-TOKEN=token-teste; path=/';
  const chamadas: { metodo: string; url: string; init: RequestInit }[] = [];
  const falso = vi.fn(async (entrada: RequestInfo | URL, init: RequestInit = {}) => {
    const url = typeof entrada === 'string' ? entrada : entrada.toString();
    const metodo = (init.method ?? 'GET').toUpperCase();
    chamadas.push({ metodo, url, init });
    const caminho = url.split('?')[0]!;
    const chave =
      Object.keys(rotas).find((k) => k === `${metodo} ${url}`) ??
      Object.keys(rotas).find((k) => k === `${metodo} ${caminho}`);
    const definicao = chave
      ? rotas[chave]!
      : {
          status: 404,
          corpo: { code: 'RESOURCE_NOT_FOUND', message: 'Não encontramos o que você procurou.' },
        };
    const resposta = typeof definicao === 'function' ? await definicao(url, init) : definicao;
    const status = resposta.status ?? 200;
    const corpo = resposta.corpo;
    const ehBlob = corpo instanceof Blob;
    return new Response(
      status === 204 || corpo === undefined ? null : ehBlob ? corpo : JSON.stringify(corpo),
      {
        status,
        headers: {
          'Content-Type': ehBlob ? 'application/pdf' : 'application/json',
          ...(resposta.cabecalhos ?? {}),
        },
      },
    );
  });
  vi.stubGlobal('fetch', falso);
  return { fetch: falso, chamadas };
}

export const SESSAO_CLARA = {
  teacher: { id: '0190a000-0000-7000-8000-000000000001', name: 'Clara Souza' },
  sessionExpiresAt: '2026-09-21T23:00:00Z',
  telegramLinked: false,
};

/** Renderiza as rotas dadas com QueryClient, tema e roteador em memória. */
export function renderizarRotas(rotas: RouteObject[], caminhoInicial: string) {
  const cliente = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  const roteador = createMemoryRouter(rotas, { initialEntries: [caminhoInicial] });
  const resultado = render(
    <QueryClientProvider client={cliente}>
      <TemaProvedor>
        <RouterProvider router={roteador} />
      </TemaProvedor>
    </QueryClientProvider>,
  );
  return { ...resultado, roteador, cliente };
}

/** Renderiza um componente isolado com os provedores. */
export function renderizar(elemento: ReactNode, caminho = '/') {
  return renderizarRotas([{ path: '*', element: elemento }], caminho);
}
