import { useEffect, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { SESSION_EXPIRED_EVENT } from '../services/apiClient';
import { SESSION_KEY, useSession } from '../services/session';
import { LoadingState } from '../components/LoadingState';
import { ErrorState } from '../components/ErrorState';

/** Caminho de /entrar guardando o destino atual. */
export function signInRoute(destination: string, reason?: string): string {
  const params = new URLSearchParams();
  if (destination && destination !== '/' && !destination.startsWith('/sign-in'))
    params.set('destination', destination);
  if (reason) params.set('reason', reason);
  const query = params.toString();
  return query ? `/sign-in?${query}` : '/sign-in';
}

/**
 * Protege as rotas autenticadas: sem sessão, ou diante de qualquer 401, leva a /entrar guardando o
 * destino (caso de borda "sessão expirada").
 */
export function SessionGuard({ children }: { children: ReactNode }) {
  const session = useSession();
  const local = useLocation();
  const navigate = useNavigate();
  const client = useQueryClient();
  const destination = `${local.pathname}${local.search}`;

  useEffect(() => {
    const onExpire = (event: Event) => {
      const reason = (event as CustomEvent<string>).detail;
      client.clear();
      client.setQueryData(SESSION_KEY, null);
      navigate(signInRoute(destination, reason), { replace: true });
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpire);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpire);
  }, [client, navigate, destination]);

  if (session.isPending) return <LoadingState label="Carregando" />;
  if (session.isError) return <ErrorState error={session.error} onRetry={() => session.refetch()} />;
  if (!session.data) return <Navigate to={signInRoute(destination)} replace />;
  return <>{children}</>;
}
