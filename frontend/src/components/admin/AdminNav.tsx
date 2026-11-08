import { NavLink } from 'react-router';
import { msg } from '../../messages/messages';
import { Icon } from '../Icon';

export const ADMIN_NAV_ITEMS: { key: string; label: string; icon: string; route: string }[] = [
  { key: 'teachers', label: 'Professores', icon: 'school', route: '/admin/teachers' },
  { key: 'students', label: 'Alunos', icon: 'group', route: '/admin/students' },
  { key: 'history', label: msg('SCHOOL_HISTORY_NAV'), icon: 'history', route: '/admin/history' },
  { key: 'audit', label: msg('AUDIT_NAV'), icon: 'shield_person', route: '/admin/audit' },
  { key: 'costs', label: msg('OPERATIONS_NAV_COSTS'), icon: 'timer', route: '/admin/operations/costs' },
  { key: 'alerts', label: msg('OPERATIONS_NAV_ALERTS'), icon: 'warning', route: '/admin/operations/alerts' },
  { key: 'consents', label: msg('CONSENT_NAV'), icon: 'verified_user', route: '/admin/consents/pending' },
  {
    key: 'administrators',
    label: 'Administradores',
    icon: 'admin_panel_settings',
    route: '/admin/administrators',
  },
];

/**
 * Navegação da área administrativa: barra inferior em telas estreitas, lateral em largas.
 * A página atual leva `aria-current="page"` (vem do `NavLink`).
 */
export function AdminNav({ placement }: { placement: 'bottom' | 'side' }) {
  return (
    <nav aria-label={msg('ADMIN_AREA_TITLE')} className={`admin-nav admin-nav--${placement}`}>
      {ADMIN_NAV_ITEMS.map((item) => (
        <NavLink key={item.key} to={item.route} className="admin-nav__item">
          <Icon name={item.icon} />
          <span>{item.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
