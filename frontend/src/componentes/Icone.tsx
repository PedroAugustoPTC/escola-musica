/** Ícone Material Symbols Outlined (decorativo por padrão). */
export function Icone({
  nome,
  tamanho,
  className,
  rotulo,
}: {
  nome: string;
  tamanho?: 'pequeno' | 'medio' | 'grande';
  className?: string;
  rotulo?: string;
}) {
  const classes = ['icone', tamanho ? `icone-${tamanho}` : '', className ?? ''].filter(Boolean).join(' ');
  return rotulo ? (
    <span className={classes} role="img" aria-label={rotulo}>
      {nome}
    </span>
  ) : (
    <span className={classes} aria-hidden="true">
      {nome}
    </span>
  );
}
