import type { RouteObject } from 'react-router';
import { adminRoutes } from '../../../src/app/adminRoutes';
import { renderAsAdministrator, type SimulatedResponse, type SimulatedRoute } from '../../utils';
import { ADMIN_SESSION } from '../../support/adminFixtures';

/**
 * Sessão do administrador com validade no futuro: o relógio da sessão leva à entrada quando a hora
 * passa, e o instante fixo de `ADMIN_SESSION` já ficou para trás. 14:00 UTC = 11:00 na escola.
 */
export const LIVE_ADMIN_SESSION = { ...ADMIN_SESSION, expiresAt: '2099-09-21T14:00:00Z' };

/** Renderiza as rotas da área administrativa já com a sessão do administrador válida. */
export function renderAdminArea(
  path: string,
  apiRoutes: Record<string, SimulatedResponse | SimulatedRoute> = {},
  routes: RouteObject[] = adminRoutes,
) {
  return renderAsAdministrator(routes, path, {
    'GET /api/v1/admin/session': { body: LIVE_ADMIN_SESSION },
    ...apiRoutes,
  });
}
