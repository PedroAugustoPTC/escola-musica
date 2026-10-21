import type { RouteObject } from 'react-router';
import { AdminSessionGuard } from './AdminSessionGuard';
import { AdminLayout } from './AdminLayout';
import { AdminSignInScreen } from '../pages/admin/AdminSignInScreen';
import { AdminHomeScreen } from '../pages/admin/AdminHomeScreen';
import { EmptyState } from '../components/EmptyState';

/** Página não encontrada dentro da moldura administrativa (rota `/admin/**` desconhecida com sessão). */
function AdminPageNotFound() {
  return (
    <div className="page page--narrow">
      <EmptyState icon="construction" text="Página não encontrada" />
    </div>
  );
}

/**
 * Rotas da área administrativa (`/admin/**`), somente Web (contracts/portal-screens.md §1).
 * Entrada e ativação são públicas; o restante fica atrás do `AdminSessionGuard` e do `AdminLayout`.
 */
export const adminRoutes: RouteObject[] = [
  { path: '/admin/sign-in', element: <AdminSignInScreen /> },
  {
    element: (
      <AdminSessionGuard>
        <AdminLayout />
      </AdminSessionGuard>
    ),
    children: [
      { path: '/admin', element: <AdminHomeScreen /> },
      { path: '/admin/*', element: <AdminPageNotFound /> },
    ],
  },
];
