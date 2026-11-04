import type { RouteObject } from 'react-router';
import { AdminSessionGuard } from './AdminSessionGuard';
import { AdminLayout } from './AdminLayout';
import { AdminSignInScreen } from '../pages/admin/AdminSignInScreen';
import { AdminHomeScreen } from '../pages/admin/AdminHomeScreen';
import { TeachersScreen } from '../pages/admin/TeachersScreen';
import { TeacherFormScreen } from '../pages/admin/TeacherFormScreen';
import { TeacherDetailScreen } from '../pages/admin/TeacherDetailScreen';
import { StudentsScreen } from '../pages/admin/StudentsScreen';
import { StudentFormScreen } from '../pages/admin/StudentFormScreen';
import { StudentDetailScreen } from '../pages/admin/StudentDetailScreen';
import { SchoolHistoryScreen } from '../pages/admin/SchoolHistoryScreen';
import { AuditTrailScreen } from '../pages/admin/AuditTrailScreen';
import { AdministratorsScreen } from '../pages/admin/AdministratorsScreen';
import { ActivateScreen } from '../pages/admin/ActivateScreen';
import { ChangePasswordScreen } from '../pages/admin/ChangePasswordScreen';
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
  { path: '/admin/activate', element: <ActivateScreen /> },
  {
    element: (
      <AdminSessionGuard>
        <AdminLayout />
      </AdminSessionGuard>
    ),
    children: [
      { path: '/admin', element: <AdminHomeScreen /> },
      { path: '/admin/change-password', element: <ChangePasswordScreen /> },
      { path: '/admin/administrators', element: <AdministratorsScreen /> },
      { path: '/admin/teachers', element: <TeachersScreen /> },
      { path: '/admin/teachers/new', element: <TeacherFormScreen /> },
      { path: '/admin/teachers/:teacherId', element: <TeacherDetailScreen /> },
      { path: '/admin/students', element: <StudentsScreen /> },
      { path: '/admin/students/new', element: <StudentFormScreen /> },
      { path: '/admin/students/:studentId', element: <StudentDetailScreen /> },
      { path: '/admin/history', element: <SchoolHistoryScreen /> },
      { path: '/admin/audit', element: <AuditTrailScreen /> },
      { path: '/admin/*', element: <AdminPageNotFound /> },
    ],
  },
];
