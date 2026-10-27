import { Link } from 'react-router';
import { ErrorAlert } from '../../components/ErrorState';
import { Icon } from '../../components/Icon';
import { LoadingState } from '../../components/LoadingState';
import { msg } from '../../messages/messages';
import { useAdminCounts } from '../../services/admin/home';
import { useAdminSession } from '../../services/admin/session';

interface Shortcut {
  to: string;
  icon: string;
  title: string;
  count?: number;
  newTo: string;
  newLabel: string;
}

/** A04 — Início da área administrativa: atalhos e contagens. */
export function AdminHomeScreen() {
  const session = useAdminSession();
  const counts = useAdminCounts();
  const data = counts.data;

  const shortcuts: Shortcut[] = [
    {
      to: '/admin/teachers',
      icon: 'school',
      title: 'Professores',
      count: data?.activeTeachers,
      newTo: '/admin/teachers/new',
      newLabel: 'Cadastrar professor',
    },
    {
      to: '/admin/students',
      icon: 'group',
      title: 'Alunos',
      count: data?.activeStudents,
      newTo: '/admin/students/new',
      newLabel: 'Cadastrar aluno',
    },
    {
      to: '/admin/administrators',
      icon: 'admin_panel_settings',
      title: 'Administradores',
      count: data?.activeAdministrators,
      newTo: '/admin/administrators',
      newLabel: 'Criar administrador',
    },
  ];

  return (
    <div className="page page--wide">
      <div>
        <div className="kicker">{msg('ADMIN_AREA_TITLE')}</div>
        <h1 className="page-title">Início</h1>
        {session.data ? <p className="subtitle">Olá, {session.data.administrator.name}.</p> : null}
      </div>
      {counts.isPending ? <LoadingState label="Carregando contagens" rows={1} height={120} /> : null}
      {counts.isError ? <ErrorAlert error={counts.error} onRetry={() => void counts.refetch()} /> : null}
      <ul className="admin-shortcuts">
        {shortcuts.map((shortcut) => (
          <li key={shortcut.to} className="card admin-shortcuts__item">
            <div className="admin-shortcuts__head">
              <Icon name={shortcut.icon} size="large" />
              <h2 className="card-title">{shortcut.title}</h2>
            </div>
            {shortcut.count !== undefined ? (
              <p className="admin-shortcuts__count">
                <span className="admin-shortcuts__number">{shortcut.count}</span> ativo(s)
              </p>
            ) : null}
            <div className="admin-shortcuts__links">
              <Link to={shortcut.to} className="button button--secondary">
                Ver {shortcut.title.toLowerCase()}
              </Link>
              <Link to={shortcut.newTo} className="button button--link">
                {shortcut.newLabel}
              </Link>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
