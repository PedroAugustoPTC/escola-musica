/** Estado de carregamento com esqueletos pulsantes (tela 03c e 12c). */
export function EstadoCarregando({
  rotulo = 'Carregando',
  linhas = 3,
  altura = 92,
}: {
  rotulo?: string;
  linhas?: number;
  altura?: number;
}) {
  return (
    <div className="pagina pagina--media" aria-busy="true" aria-label={rotulo} role="status">
      {Array.from({ length: linhas }, (_, i) => (
        <div key={i} className="esqueleto" style={{ height: altura, borderRadius: 8 }} />
      ))}
    </div>
  );
}

/** Esqueletos no formato dos cartões de aluno (tela 03c). */
export function EsqueletoCartoes({ rotulo, quantidade = 3 }: { rotulo: string; quantidade?: number }) {
  return (
    <div className="grade-cartoes" aria-busy="true" aria-label={rotulo} role="status">
      {Array.from({ length: quantidade }, (_, i) => (
        <div key={i} className="cartao">
          <div className="esqueleto" style={{ width: '55%', height: 22 }} />
          <div className="esqueleto" style={{ width: '30%', height: 14 }} />
          <div className="esqueleto" style={{ height: 48, borderRadius: 6 }} />
        </div>
      ))}
    </div>
  );
}
