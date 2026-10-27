import { useEffect, useRef } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import { ADMIN_SESSION_EXPIRED_EVENT } from '../services/apiClient';
import { useAdminSession, useAdminSignOut } from '../services/admin/session';
import { msg } from '../messages/messages';
import { AdminNav } from '../components/admin/AdminNav';
import { SessionClock } from '../components/admin/SessionClock';
import { Icon } from '../components/Icon';
import { errorText } from '../components/ErrorState';
import { useIsMobile } from './useIsMobile';
import { useTheme } from './ThemeProvider';

/**
 * Moldura da área administrativa: selo "Área administrativa" (distingue do portal do professor),
 * navegação, relógio da sessão fixa, tema claro/escuro e "Sair". Ao expirar, devolve o controle ao
 * `AdminSessionGuard` pelo evento de sessão expirada do administrador.
 */
export function AdminLayout() {
  const mobile = useIsMobile();
  const navigate = useNavigate();
  const { theme, toggle, ink } = useTheme();
  const session = useAdminSession();
  const signOut = useAdminSignOut();
  const dark = theme === 'dark';
  const main = useRef<HTMLElement>(null);
  const { pathname } = useLocation();
  const previousPath = useRef(pathname);
  const data = session.data;

  // ao trocar de rota o foco vai para o título da página (ou, sem título ainda, para o conteúdo): sem isso o
  // leitor de tela e o teclado ficam presos no link da navegação, no meio da página anterior
  useEffect(() => {
    if (previousPath.current === pathname) return;
    previousPath.current = pathname;
    const heading = main.current?.querySelector<HTMLElement>('h1');
    if (heading) {
      if (!heading.hasAttribute('tabindex')) heading.setAttribute('tabindex', '-1');
      heading.focus();
    } else {
      main.current?.focus();
    }
  }, [pathname]);
  // senha provisória: só a troca de senha é permitida, então a navegação não é oferecida
  const restricted = data?.mustChangePassword === true;

  const expire = () => {
    window.dispatchEvent(new CustomEvent(ADMIN_SESSION_EXPIRED_EVENT, { detail: 'SESSION_EXPIRED' }));
  };

  const leave = () =>
    signOut.mutate(undefined, { onSuccess: () => navigate('/admin/sign-in', { replace: true }) });

  const actions = (
    <div className="admin-layout__actions">
      <button type="button" className="admin-layout__action" aria-pressed={dark} onClick={toggle}>
        <Icon name="dark_mode" />
        <span>Modo escuro</span>
      </button>
      <button type="button" className="admin-layout__action" disabled={signOut.isPending} onClick={leave}>
        <Icon name="logout" />
        <span>Sair</span>
      </button>
    </div>
  );

  const identity = (
    <div className="admin-layout__identity">
      <span className="admin-layout__seal">
        <Icon name="shield_person" size="small" />
        {msg('ADMIN_AREA_TITLE')}
      </span>
      {data ? <span className="admin-layout__name">{data.administrator.name}</span> : null}
    </div>
  );

  return (
    <div className={`app admin-layout admin-layout--${mobile ? 'narrow' : 'wide'}`}>
      <a className="visually-hidden admin-layout__skip" href="#content">
        Ir para o conteúdo
      </a>
      {mobile ? (
        <header className="admin-layout__header">
          <Link to="/admin" className="admin-layout__brand" aria-label="Início da área administrativa">
            <img src={`/brand/fb-monogram-${ink}.png`} alt="" width={30} height={30} />
          </Link>
          {identity}
          {actions}
        </header>
      ) : (
        <aside className="admin-layout__side">
          <Link to="/admin" className="admin-layout__brand" aria-label="Início da área administrativa">
            <img src="/brand/fb-logo-metal.png" alt="Instituto Musical Felipe Blaft" width={170} />
          </Link>
          {identity}
          {restricted ? null : <AdminNav placement="side" />}
          {actions}
        </aside>
      )}
      <div className="admin-layout__body">
        {data ? <SessionClock expiresAt={data.expiresAt} onExpired={expire} /> : null}
        {signOut.isError ? (
          <div role="alert" className="alert alert--error">
            <Icon name="error" />
            <div className="alert__text">{errorText(signOut.error)}</div>
          </div>
        ) : null}
        <main id="content" className="admin-layout__main" tabIndex={-1} ref={main}>
          <Outlet />
        </main>
      </div>
      {mobile && !restricted ? <AdminNav placement="bottom" /> : null}
    </div>
  );
}
