import { useEffect, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { EVENTO_SESSAO_EXPIRADA } from '../servicos/clienteApi';
import { CHAVE_SESSAO, useSessao } from '../servicos/sessao';
import { EstadoCarregando } from '../componentes/EstadoCarregando';
import { EstadoErro } from '../componentes/EstadoErro';

/** Caminho de /entrar guardando o destino atual. */
export function rotaEntrar(destino: string, motivo?: string): string {
  const parametros = new URLSearchParams();
  if (destino && destino !== '/' && !destino.startsWith('/entrar')) parametros.set('destino', destino);
  if (motivo) parametros.set('motivo', motivo);
  const consulta = parametros.toString();
  return consulta ? `/entrar?${consulta}` : '/entrar';
}

/**
 * Protege as rotas autenticadas: sem sessão, ou diante de qualquer 401, leva a /entrar guardando o
 * destino (caso de borda "sessão expirada").
 */
export function GuardaSessao({ children }: { children: ReactNode }) {
  const sessao = useSessao();
  const local = useLocation();
  const navegar = useNavigate();
  const cliente = useQueryClient();
  const destino = `${local.pathname}${local.search}`;

  useEffect(() => {
    const aoExpirar = (evento: Event) => {
      const motivo = (evento as CustomEvent<string>).detail;
      cliente.clear();
      cliente.setQueryData(CHAVE_SESSAO, null);
      navegar(rotaEntrar(destino, motivo), { replace: true });
    };
    window.addEventListener(EVENTO_SESSAO_EXPIRADA, aoExpirar);
    return () => window.removeEventListener(EVENTO_SESSAO_EXPIRADA, aoExpirar);
  }, [cliente, navegar, destino]);

  if (sessao.isPending) return <EstadoCarregando rotulo="Carregando" />;
  if (sessao.isError) return <EstadoErro erro={sessao.error} aoTentarDeNovo={() => sessao.refetch()} />;
  if (!sessao.data) return <Navigate to={rotaEntrar(destino)} replace />;
  return <>{children}</>;
}
