import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Icon } from './Icon';

export type ButtonVariant =
  'primary' | 'secondary' | 'danger' | 'filled-danger' | 'notice' | 'link' | 'link-danger' | 'icon';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: 'large' | 'normal' | 'compact';
  icon?: string;
  loading?: boolean;
  block?: boolean;
  children?: ReactNode;
}

/** Botão do design system; `loading` mostra o indicador e bloqueia novo clique. */
export function Button({
  variant = 'primary',
  size = 'normal',
  icon,
  loading = false,
  block = false,
  className,
  disabled,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  const classes = [
    'button',
    `button--${variant}`,
    size !== 'normal' ? `button--${size}` : '',
    block ? 'button--block' : '',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');
  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? (
        <span className="spinner" aria-hidden="true" />
      ) : icon ? (
        <Icon name={icon} size="medium" />
      ) : null}
      {children}
    </button>
  );
}
