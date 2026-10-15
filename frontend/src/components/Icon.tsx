/** Ícone Material Symbols Outlined (decorativo por padrão). */
export function Icon({
  name,
  size,
  className,
  label,
}: {
  name: string;
  size?: 'small' | 'medium' | 'large';
  className?: string;
  label?: string;
}) {
  const classes = ['icon', size ? `icon-${size}` : '', className ?? ''].filter(Boolean).join(' ');
  return label ? (
    <span className={classes} role="img" aria-label={label}>
      {name}
    </span>
  ) : (
    <span className={classes} aria-hidden="true">
      {name}
    </span>
  );
}
