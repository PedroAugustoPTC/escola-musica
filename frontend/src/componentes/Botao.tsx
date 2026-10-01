import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Icone } from './Icone';

export type VarianteBotao =
  'primario' | 'secundario' | 'perigo' | 'perigo-cheio' | 'aviso' | 'link' | 'link-perigo' | 'icone';

interface PropsBotao extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: VarianteBotao;
  tamanho?: 'grande' | 'normal' | 'compacto';
  icone?: string;
  carregando?: boolean;
  bloco?: boolean;
  children?: ReactNode;
}

/** Botão do design system; `carregando` mostra o indicador e bloqueia novo clique. */
export function Botao({
  variante = 'primario',
  tamanho = 'normal',
  icone,
  carregando = false,
  bloco = false,
  className,
  disabled,
  children,
  type = 'button',
  ...resto
}: PropsBotao) {
  const classes = [
    'botao',
    `botao--${variante}`,
    tamanho !== 'normal' ? `botao--${tamanho}` : '',
    bloco ? 'botao--bloco' : '',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');
  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || carregando}
      aria-busy={carregando || undefined}
      {...resto}
    >
      {carregando ? (
        <span className="girador" aria-hidden="true" />
      ) : icone ? (
        <Icone nome={icone} tamanho="medio" />
      ) : null}
      {children}
    </button>
  );
}
