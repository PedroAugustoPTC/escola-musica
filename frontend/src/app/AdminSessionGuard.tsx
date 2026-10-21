import { useEffect, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import {
  ADMIN_PASSWORD_CHANGE_REQUIRED_EVENT,
  ADMIN_SESSION_EXPIRED_EVENT,
  ApiError,
} from '../services/apiClient';
import {
  ADMIN_SESSION_KEY,
  adminSignInRoute,
  forgetAdminSession,
  useAdminSession,
  type AdministratorSession,
} from '../services/admin/session';
import { LoadingState } from '../components/LoadingState';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';

export const CHANGE_PASSWORD_ROUTE = '/admin/change-password';

/** "Página não encontrada": a área administrativa não é revelada a quem só tem outra credencial. */
function AdminNotFound() {
  return (
    <div className="app">
      <main id="content" className="app__main">
        <div className="page page--narrow">
          <EmptyState
            icon="construction"
            text="Página não encontrada"
            action={
              <Link to="/" className="button button--secondary">
                Ir para o início
              </Link>
            }
          />
        </div>
      </main>
    </div>
  );
}

/**
 * Protege as rotas da área administrativa. Independente do `SessionGuard`: escuta só os eventos do
 * administrador, limpa só o cache dele e leva só à entrada dele, guardando o destino. Enquanto a senha for
 * provisória (`mustChangePassword`), qualquer outra rota administrativa leva à troca de senha.
 */
export function AdminSessionGuard({ children }: { children: ReactNode }) {
  const session = useAdminSession();
  const location = useLocation();
  const navigate = useNavigate();
  const client = useQueryClient();
  const destination = `${location.pathname}${location.search}`;

  useEffect(() => {
    const onExpire = (event: Event) => {
      const reason = (event as CustomEvent<string>).detail;
      forgetAdminSession(client);
      navigate(adminSignInRoute(destination, reason), { replace: true });
    };
    const onPasswordChangeRequired = () => {
      client.setQueryData<AdministratorSession | null>(ADMIN_SESSION_KEY, (current) =>
        current ? { ...current, mustChangePassword: true } : current,
      );
      navigate(CHANGE_PASSWORD_ROUTE, { replace: true });
    };
    window.addEventListener(ADMIN_SESSION_EXPIRED_EVENT, onExpire);
    window.addEventListener(ADMIN_PASSWORD_CHANGE_REQUIRED_EVENT, onPasswordChangeRequired);
    return () => {
      window.removeEventListener(ADMIN_SESSION_EXPIRED_EVENT, onExpire);
      window.removeEventListener(ADMIN_PASSWORD_CHANGE_REQUIRED_EVENT, onPasswordChangeRequired);
    };
  }, [client, navigate, destination]);

  if (session.isPending) return <LoadingState label="Carregando" />;
  if (session.isError) {
    if (session.error instanceof ApiError && session.error.status === 404) return <AdminNotFound />;
    return (
      <ErrorState
        error={session.error}
        onRetry={() => {
          void session.refetch();
        }}
      />
    );
  }
  if (!session.data) return <Navigate to={adminSignInRoute(destination)} replace />;
  if (session.data.mustChangePassword && location.pathname !== CHANGE_PASSWORD_ROUTE) {
    return <Navigate to={CHANGE_PASSWORD_ROUTE} replace />;
  }
  return <>{children}</>;
}
