import { useEffect, useId, useRef, type InputHTMLAttributes, type ReactNode } from 'react';
import { Button } from '../Button';
import { Icon } from '../Icon';

/** Situação com ícone e texto (nunca só cor): "Ativo" / "Inativo". */
export function StatusBadge({ active }: { active: boolean }) {
  return active ? (
    <span className="status-badge status-badge--active">
      <Icon name="check_circle" size="small" />
      Ativo
    </span>
  ) : (
    <span className="status-badge status-badge--inactive">
      <Icon name="block" size="small" />
      Inativo
    </span>
  );
}

/** Campo de texto com rótulo explícito, ajuda e erro ligados por `aria-describedby`. */
export function TextField({
  label,
  error,
  help,
  id: givenId,
  ...input
}: { label: string; error?: string; help?: string } & InputHTMLAttributes<HTMLInputElement>) {
  const generated = useId();
  const id = givenId ?? generated;
  const helpId = `${id}-help`;
  const errorId = `${id}-error`;
  const describedBy = [error ? errorId : null, help ? helpId : null].filter(Boolean).join(' ') || undefined;
  return (
    <div className="field">
      <label htmlFor={id} className="field__label">
        {label}
      </label>
      <input
        {...input}
        id={id}
        className={`input${input.className ? ` ${input.className}` : ''}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
      />
      {error ? (
        <div id={errorId} className="field__error">
          <Icon name="error" size="small" />
          {error}
        </div>
      ) : null}
      {help ? (
        <div id={helpId} className="field__help">
          {help}
        </div>
      ) : null}
    </div>
  );
}

/**
 * Resumo de erros no topo do formulário: `role="alert"` e recebe o foco quando aparece, para o leitor
 * de tela anunciar e o teclado já estar no problema.
 */
export function FormErrorSummary({ messages }: { messages: string[] }) {
  const box = useRef<HTMLDivElement>(null);
  const signature = messages.join('|');
  useEffect(() => {
    if (signature) box.current?.focus();
  }, [signature]);
  if (messages.length === 0) return null;
  return (
    <div ref={box} role="alert" tabIndex={-1} className="alert alert--error admin-form__summary">
      <Icon name="error" />
      <div className="alert__text">
        {messages.length === 1 ? (
          messages[0]
        ) : (
          <ul>
            {messages.map((text, index) => (
              <li key={`${index}-${text}`}>{text}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

/** Controle segmentado de filtro (botões com `aria-pressed`). */
export function SegmentedFilter<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
}) {
  return (
    <div role="group" aria-label={label} className="segmented">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className="segmented__option"
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

/** Paginação "Anterior/Próxima" com a página atual e o total. */
export function Pagination({
  page,
  size,
  total,
  onPage,
  children,
}: {
  page: number;
  size: number;
  total: number;
  onPage: (page: number) => void;
  /** Unidade do total, ex.: "professor(es)". */
  children: ReactNode;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <nav aria-label="Paginação" className="admin-pagination">
      <span className="support-text">
        Página {page + 1} de {pages} · {total} {children}
      </span>
      <div className="admin-actions">
        <Button variant="secondary" size="compact" disabled={page <= 0} onClick={() => onPage(page - 1)}>
          Anterior
        </Button>
        <Button
          variant="secondary"
          size="compact"
          disabled={page + 1 >= pages}
          onClick={() => onPage(page + 1)}
        >
          Próxima
        </Button>
      </div>
    </nav>
  );
}

/** Busca em `<form role="search">`; o termo só sai do campo ao enviar. */
export function SearchForm({
  label,
  value,
  onChange,
  onSubmit,
  error,
  children,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  error?: string;
  children?: ReactNode;
}) {
  const errorId = useId();
  return (
    <form
      role="search"
      className="admin-search"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
    >
      <div className="field" style={{ flex: 1 }}>
        <label className="search">
          <span className="visually-hidden">{label}</span>
          <Icon name="search" />
          <input
            type="search"
            className="input"
            value={value}
            placeholder={label}
            maxLength={100}
            autoComplete="off"
            autoCapitalize="off"
            spellCheck={false}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
            onChange={(event) => onChange(event.target.value)}
          />
        </label>
        {error ? (
          <div id={errorId} className="field__error">
            <Icon name="error" size="small" />
            {error}
          </div>
        ) : null}
      </div>
      <Button type="submit" variant="secondary">
        Buscar
      </Button>
      {children}
    </form>
  );
}
