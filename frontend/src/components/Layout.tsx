import type { ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { useIsMobile } from '../app/useIsMobile';
import { Header, NAVIGATION_ITEMS, type HeaderContext, type NavigationItem } from './Header';
import { Icon } from './Icon';

/**
 * Moldura das telas autenticadas: cabeçalho, conteúdo rolável, barra de ações fixa (gravação,
 * perguntas, conferência, semestral), navegação inferior no celular e rodapé no computador.
 */
export function Layout({
  context,
  activeNav,
  bottomNav = false,
  actionsBar,
  narrowBar = false,
  children,
}: {
  context?: HeaderContext;
  activeNav?: NavigationItem;
  bottomNav?: boolean;
  actionsBar?: ReactNode;
  narrowBar?: boolean;
  children: ReactNode;
}) {
  const mobile = useIsMobile();
  const navigate = useNavigate();
  return (
    <div className="app">
      <a className="visually-hidden" href="#content">
        Ir para o conteúdo
      </a>
      <Header context={context} activeNav={activeNav} />
      <main id="content" className="app__main" tabIndex={-1}>
        {children}
        {!mobile && !actionsBar ? (
          <footer className="footer">
            <span className="footer__brand">INSTITUTO MUSICAL FELIPE BLAFT</span>
            <span>Portal do Professor · Relatórios pedagógicos</span>
          </footer>
        ) : null}
      </main>
      {actionsBar ? (
        <div className="actions-bar">
          <div className={`actions-bar__content${narrowBar ? ' actions-bar__content--narrow' : ''}`}>
            {actionsBar}
          </div>
        </div>
      ) : null}
      {mobile && bottomNav ? (
        <nav aria-label="Principal" className="bottom-nav">
          {NAVIGATION_ITEMS.map((item) => (
            <button
              key={item.key}
              type="button"
              className="bottom-nav__item"
              aria-current={activeNav === item.key ? 'page' : undefined}
              onClick={() => navigate(item.route)}
            >
              <Icon name={item.icon} />
              {item.label}
            </button>
          ))}
        </nav>
      ) : null}
    </div>
  );
}
