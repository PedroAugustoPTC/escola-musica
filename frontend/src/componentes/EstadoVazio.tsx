import type { ReactNode } from 'react';
import { Icone } from './Icone';
import { useTema } from '../app/TemaProvedor';

/** Estado vazio: ícone ou monograma, texto e ação opcional (telas 02b, 03b, 10c, 12b). */
export function EstadoVazio({
  icone,
  monograma = false,
  titulo,
  texto,
  acao,
  solido = false,
}: {
  icone?: string;
  monograma?: boolean;
  titulo?: string;
  texto: string;
  acao?: ReactNode;
  solido?: boolean;
}) {
  const { tintaOuro } = useTema();
  return (
    <div className={`estado-vazio${solido ? ' estado-vazio--solido' : ''}`} role="status">
      {monograma ? (
        <img
          src={`/marca/fb-monograma-${tintaOuro}.png`}
          alt=""
          width={56}
          height={56}
          style={{ objectFit: 'contain' }}
        />
      ) : icone ? (
        <Icone nome={icone} className="estado-vazio__icone" />
      ) : null}
      {titulo ? <h2 className="titulo-cartao">{titulo}</h2> : null}
      <p className="estado-vazio__texto">{texto}</p>
      {acao}
    </div>
  );
}
