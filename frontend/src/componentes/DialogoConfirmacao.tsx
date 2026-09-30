import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Botao } from './Botao';
import { Icone } from './Icone';

interface PropsDialogo {
  aberto: boolean;
  titulo: string;
  texto: ReactNode;
  icone: string;
  perigo?: boolean;
  rotuloConfirmar: string;
  rotuloVoltar?: string;
  carregando?: boolean;
  erro?: ReactNode;
  aoConfirmar: () => void;
  aoFechar: () => void;
}

const FOCAVEIS =
  'button:not([disabled]), [href], input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Diálogo de confirmação (08e, 08f, 04b): foco preso dentro do diálogo, `Esc` fecha, foco volta ao
 * elemento que o abriu. A confirmação só avança com a resposta 2xx (quem chama controla).
 */
export function DialogoConfirmacao({
  aberto,
  titulo,
  texto,
  icone,
  perigo = false,
  rotuloConfirmar,
  rotuloVoltar = 'Voltar',
  carregando = false,
  erro,
  aoConfirmar,
  aoFechar,
}: PropsDialogo) {
  const idTitulo = useId();
  const idTexto = useId();
  const caixa = useRef<HTMLDivElement>(null);
  const aoFecharRef = useRef(aoFechar);
  useEffect(() => {
    aoFecharRef.current = aoFechar;
  }, [aoFechar]);

  useEffect(() => {
    if (!aberto) return;
    const anterior = document.activeElement as HTMLElement | null;
    const primeiro = caixa.current?.querySelector<HTMLElement>(FOCAVEIS);
    primeiro?.focus();
    const aoTeclar = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') {
        evento.preventDefault();
        aoFecharRef.current();
        return;
      }
      if (evento.key !== 'Tab' || !caixa.current) return;
      const focaveis = Array.from(caixa.current.querySelectorAll<HTMLElement>(FOCAVEIS));
      if (focaveis.length === 0) return;
      const inicio = focaveis[0]!;
      const fim = focaveis[focaveis.length - 1]!;
      if (evento.shiftKey && document.activeElement === inicio) {
        evento.preventDefault();
        fim.focus();
      } else if (!evento.shiftKey && document.activeElement === fim) {
        evento.preventDefault();
        inicio.focus();
      }
    };
    document.addEventListener('keydown', aoTeclar);
    return () => {
      document.removeEventListener('keydown', aoTeclar);
      anterior?.focus?.();
    };
  }, [aberto]);

  if (!aberto) return null;

  return createPortal(
    <div className="dialogo-fundo">
      <div
        ref={caixa}
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitulo}
        aria-describedby={idTexto}
        className="dialogo"
      >
        <span
          className={`circulo-icone dialogo__icone ${perigo ? 'circulo-icone--erro' : 'circulo-icone--neutro'}`}
        >
          <Icone nome={icone} />
        </span>
        <h2 id={idTitulo} className="dialogo__titulo">
          {titulo}
        </h2>
        <p id={idTexto} className="dialogo__texto">
          {texto}
        </p>
        {erro ? (
          <div role="alert" className="alerta alerta--erro">
            <Icone nome="error" />
            <div className="alerta__texto">{erro}</div>
          </div>
        ) : null}
        <div className="dialogo__acoes">
          <Botao
            variante={perigo ? 'perigo-cheio' : 'primario'}
            tamanho="grande"
            carregando={carregando}
            onClick={aoConfirmar}
          >
            {rotuloConfirmar}
          </Botao>
          <Botao variante="secundario" onClick={aoFechar} disabled={carregando}>
            {rotuloVoltar}
          </Botao>
        </div>
      </div>
    </div>,
    document.body,
  );
}
