import type { RouteObject } from 'react-router';
import { MyStudentDetailScreen } from '../pages/MyStudentDetailScreen';
import { MyStudentFormScreen } from '../pages/MyStudentFormScreen';
import { MyStudentsScreen } from '../pages/MyStudentsScreen';
import { SessionGuard } from './SessionGuard';

/** Rotas "Meus alunos" do professor (/my-students...), atras do SessionGuard (contracts/portal-screens.md, secao 1). */
export const myStudentsRoutes: RouteObject[] = [
  {
    path: '/my-students',
    element: (
      <SessionGuard>
        <MyStudentsScreen />
      </SessionGuard>
    ),
  },
  {
    path: '/my-students/new',
    element: (
      <SessionGuard>
        <MyStudentFormScreen />
      </SessionGuard>
    ),
  },
  {
    path: '/my-students/:studentId',
    element: (
      <SessionGuard>
        <MyStudentDetailScreen />
      </SessionGuard>
    ),
  },
];
