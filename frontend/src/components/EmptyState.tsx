import type { ReactNode } from 'react';
import { Icon } from './Icon';
import { useTheme } from '../app/ThemeProvider';

/** Estado vazio: ícone ou monograma, texto e ação opcional (telas 02b, 03b, 10c, 12b). */
export function EmptyState({
  icon,
  monogram = false,
  title,
  text,
  action,
  solid = false,
}: {
  icon?: string;
  monogram?: boolean;
  title?: string;
  text: string;
  action?: ReactNode;
  solid?: boolean;
}) {
  const { goldInk } = useTheme();
  return (
    <div className={`empty-state${solid ? ' empty-state--solid' : ''}`} role="status">
      {monogram ? (
        <img
          src={`/brand/fb-monogram-${goldInk}.png`}
          alt=""
          width={56}
          height={56}
          style={{ objectFit: 'contain' }}
        />
      ) : icon ? (
        <Icon name={icon} className="empty-state__icon" />
      ) : null}
      {title ? <h2 className="card-title">{title}</h2> : null}
      <p className="empty-state__text">{text}</p>
      {action}
    </div>
  );
}
