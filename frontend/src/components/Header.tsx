import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { useTheme } from '../app/ThemeProvider';
import { useIsMobile } from '../app/useIsMobile';
import { useEndSession, useSession } from '../services/session';
import { msg } from '../messages/messages';
import { Icon } from './Icon';
import { errorText } from './ErrorState';

export type NavigationItem = 'dashboard' | 'students' | 'register';

export interface HeaderContext {
  kicker: string;
  title: string;
  backTo: string;
}

export const NAVIGATION_ITEMS: { key: NavigationItem; label: string; icon: string; route: string }[] = [
  { key: 'dashboard', label: 'Painel', icon: 'space_dashboard', route: '/' },
  { key: 'students', label: 'Alunos', icon: 'group', route: '/students' },
  { key: 'register', label: msg('REGISTER_LESSON_BUTTON'), icon: 'mic', route: '/students?register=1' },
];

/** Iniciais do primeiro e do último nome ("Miguel de Castro Torrente" → "MT"). */
export function initials(name: string): string {
  const parts = name.split(/\s+/).filter(Boolean);
  const first = parts[0]?.[0] ?? '';
  const last = parts.length > 1 ? parts[parts.length - 1]![0]! : '';
  return (first + last).toUpperCase();
}

/** Menu do professor (02c): tema claro/escuro e "Encerrar sessão" (só navega após 2xx). */
function TeacherMenu({ onClose }: { onClose: () => void }) {
  const { theme, toggle } = useTheme();
  const session = useSession();
  const end = useEndSession();
  const navigate = useNavigate();
  const name = session.data?.teacher.name ?? '';
  const dark = theme === 'dark';

  return (
    <>
      <div className="menu-backdrop" onClick={onClose} aria-hidden="true" />
      <div role="menu" aria-label="Menu do professor" className="teacher-menu">
        <div className="teacher-menu__identity">
          <div style={{ fontWeight: 600 }}>{name}</div>
          <div className="support-text">Portal do Professor</div>
        </div>
        <button
          type="button"
          role="menuitemcheckbox"
          aria-checked={dark}
          className="teacher-menu__item"
          onClick={toggle}
        >
          <Icon name="dark_mode" />
          <span style={{ flex: 1 }}>Modo escuro</span>
          <span className={`switch${dark ? ' switch--on' : ''}`} aria-hidden="true">
            <span className="switch__button" />
          </span>
        </button>
        <button
          type="button"
          role="menuitem"
          className="teacher-menu__item"
          disabled={end.isPending}
          onClick={() =>
            end.mutate(undefined, {
              onSuccess: () => navigate('/sign-in', { replace: true }),
            })
          }
        >
          <Icon name="logout" />
          <span>{msg('END_SESSION_BUTTON')}</span>
        </button>
        {end.isError ? (
          <div role="alert" className="field__error" style={{ padding: '6px 12px' }}>
            {errorText(end.error)}
          </div>
        ) : null}
      </div>
    </>
  );
}

/** Cabeçalho do portal nas versões móvel (60 px) e computador (76 px). */
export function Header({ context, activeNav }: { context?: HeaderContext; activeNav?: NavigationItem }) {
  const mobile = useIsMobile();
  const { ink } = useTheme();
  const navigate = useNavigate();
  const session = useSession();
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState('');
  const name = session.data?.teacher.name ?? '';

  const menuButton = (
    <button
      type="button"
      className="button button--icon"
      aria-label="Menu"
      aria-expanded={menuOpen}
      aria-haspopup="menu"
      onClick={() => setMenuOpen((open) => !open)}
    >
      <Icon name="menu" />
    </button>
  );

  if (mobile) {
    return (
      <header className="header header--mobile">
        {context ? (
          <>
            <button
              type="button"
              className="button button--icon"
              aria-label="Voltar"
              onClick={() => navigate(context.backTo)}
            >
              <Icon name="arrow_back" />
            </button>
            <div className="header__context">
              <div className="header__context-kicker">{context.kicker}</div>
              <div className="header__context-title">{context.title}</div>
            </div>
          </>
        ) : (
          <button
            type="button"
            className="header__brand header__brand--mobile"
            aria-label="Instituto Musical Felipe Blaft — Painel"
            onClick={() => navigate('/')}
          >
            <img
              src={`/brand/fb-monogram-${ink}.png`}
              alt=""
              width={30}
              height={30}
              style={{ objectFit: 'contain' }}
            />
            <img
              src={`/brand/fb-wordmark-${ink}.png`}
              alt=""
              width={112}
              height={25}
              style={{ objectFit: 'contain' }}
            />
          </button>
        )}
        <button
          type="button"
          className="button button--icon"
          aria-label="Buscar aluno"
          onClick={() => navigate('/students?focus=search')}
        >
          <Icon name="search" />
        </button>
        {menuButton}
        {menuOpen ? <TeacherMenu onClose={() => setMenuOpen(false)} /> : null}
      </header>
    );
  }

  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    const term = search.trim();
    navigate(term ? `/students?search=${encodeURIComponent(term)}` : '/students');
  };

  return (
    <header className="header header--desktop">
      <button
        type="button"
        className="header__brand"
        aria-label="Instituto Musical Felipe Blaft — Painel"
        onClick={() => navigate('/')}
      >
        <img
          src={`/brand/fb-monogram-${ink}.png`}
          alt=""
          width={38}
          height={38}
          style={{ objectFit: 'contain' }}
        />
        <span className="header__divider" />
        <img
          src={`/brand/fb-wordmark-${ink}.png`}
          alt=""
          width={150}
          height={33}
          style={{ objectFit: 'contain' }}
        />
      </button>
      <nav aria-label="Principal" className="header__nav">
        {NAVIGATION_ITEMS.map((item) => (
          <button
            key={item.key}
            type="button"
            className="header__nav-item"
            aria-current={activeNav === item.key ? 'page' : undefined}
            onClick={() => navigate(item.route)}
          >
            {item.label}
          </button>
        ))}
      </nav>
      <div className="header__space" />
      <form role="search" onSubmit={submitSearch}>
        <label className="header__search">
          <span className="visually-hidden">Buscar aluno</span>
          <Icon name="search" />
          <input
            type="search"
            value={search}
            placeholder="Buscar aluno"
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
      </form>
      <button
        type="button"
        className="header__teacher"
        aria-expanded={menuOpen}
        aria-haspopup="menu"
        onClick={() => setMenuOpen((open) => !open)}
      >
        <span className="initials" aria-hidden="true">
          {initials(name)}
        </span>
        <span className="header__teacher-name">{name}</span>
        <Icon name="expand_more" size="medium" />
      </button>
      {menuOpen ? <TeacherMenu onClose={() => setMenuOpen(false)} /> : null}
    </header>
  );
}
