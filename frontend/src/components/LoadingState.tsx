/** Estado de carregamento com esqueletos pulsantes (tela 03c e 12c). */
export function LoadingState({
  label = 'Carregando',
  rows = 3,
  height = 92,
}: {
  label?: string;
  rows?: number;
  height?: number;
}) {
  return (
    <div className="page page--medium" aria-busy="true" aria-label={label} role="status">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton" style={{ height, borderRadius: 8 }} />
      ))}
    </div>
  );
}

/** Esqueletos no formato dos cartões de aluno (tela 03c). */
export function CardsSkeleton({ label, quantity = 3 }: { label: string; quantity?: number }) {
  return (
    <div className="card-grid" aria-busy="true" aria-label={label} role="status">
      {Array.from({ length: quantity }, (_, i) => (
        <div key={i} className="card">
          <div className="skeleton" style={{ width: '55%', height: 22 }} />
          <div className="skeleton" style={{ width: '30%', height: 14 }} />
          <div className="skeleton" style={{ height: 48, borderRadius: 6 }} />
        </div>
      ))}
    </div>
  );
}
