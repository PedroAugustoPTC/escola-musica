import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { ErroApi } from '../servicos/clienteApi';
import { TemaProvedor } from './TemaProvedor';
import { rotas } from './rotas';

export function criarClienteConsultas(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        refetchOnWindowFocus: false,
        // erros 4xx são respostas de negócio; só falhas de rede e 5xx merecem nova tentativa
        retry: (tentativas, erro) =>
          tentativas < 2 && (!(erro instanceof ErroApi) || erro.status === 0 || erro.status >= 500),
      },
      mutations: { retry: false },
    },
  });
}

const clienteConsultas = criarClienteConsultas();
const roteador = createBrowserRouter(rotas);

export function App() {
  return (
    <QueryClientProvider client={clienteConsultas}>
      <TemaProvedor>
        <RouterProvider router={roteador} />
      </TemaProvedor>
    </QueryClientProvider>
  );
}
