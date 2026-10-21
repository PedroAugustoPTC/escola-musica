import { useQuery } from '@tanstack/react-query';
import { api } from '../apiClient';
import { ADMIN_QUERY_ROOT } from './session';

interface Total {
  total: number;
}

export interface AdminCounts {
  activeTeachers: number;
  activeStudents: number;
  activeAdministrators: number;
}

/** Contagens do início (A04): só o `total` de cada lista, sem trazer registros. */
export function useAdminCounts() {
  return useQuery<AdminCounts>({
    queryKey: [ADMIN_QUERY_ROOT, 'counts'],
    queryFn: async ({ signal }) => {
      const [teachers, students, administrators] = await Promise.all([
        api.get<Total>('/admin/teachers?active=true&size=1', signal),
        api.get<Total>('/admin/students?status=ACTIVE&size=1', signal),
        api.get<Total>('/admin/administrators?active=true&size=1', signal),
      ]);
      return {
        activeTeachers: teachers.total,
        activeStudents: students.total,
        activeAdministrators: administrators.total,
      };
    },
  });
}
