import { useId, useState, type FormEvent } from 'react';
import { Button } from '../Button';
import { Icon } from '../Icon';
import { looksLikeCpf } from './cpfShape';
import type { StudentStatus } from '../../services/myStudents';
import '../../styles/student-registry.css';

const STATUS_OPTIONS: { value: StudentStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Ativos' },
  { value: 'INACTIVE', label: 'Inativos' },
  { value: 'ALL', label: 'Todos' },
];

interface StudentSearchBarProps {
  label: string;
  /** Termo já aplicado (vira consulta). */
  term: string;
  status: StudentStatus;
  onSearch: (term: string) => void;
  onStatusChange: (status: StudentStatus) => void;
}

/**
 * Busca por nome ou instrumento (só ao enviar) e filtro de situação. Termo com forma de CPF é
 * recusado aqui: não vai à URL, à API nem ao cache de consulta.
 */
export function StudentSearchBar({ label, term, status, onSearch, onStatusChange }: StudentSearchBarProps) {
  const errorId = useId();
  const [draft, setDraft] = useState(term);
  const [appliedTerm, setAppliedTerm] = useState(term);
  const [rejected, setRejected] = useState(false);

  // o termo aplicado mudou por fora (por exemplo, "Limpar busca"): o campo acompanha
  if (appliedTerm !== term) {
    setAppliedTerm(term);
    setDraft(term);
    setRejected(false);
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const value = draft.trim();
    if (looksLikeCpf(value)) {
      setRejected(true);
      return;
    }
    setRejected(false);
    onSearch(value);
  };

  return (
    <div className="student-search">
      <form role="search" className="student-search__row" onSubmit={submit} noValidate>
        <div className="student-search__field">
          <label className="search">
            <span className="visually-hidden">{label}</span>
            <Icon name="search" />
            <input
              type="search"
              className="input"
              value={draft}
              maxLength={100}
              placeholder="Nome ou instrumento"
              autoComplete="off"
              spellCheck={false}
              aria-invalid={rejected ? true : undefined}
              aria-describedby={rejected ? errorId : undefined}
              onChange={(event) => {
                setDraft(event.target.value);
                setRejected(false);
              }}
            />
          </label>
          {rejected ? (
            <div className="field__error" role="alert">
              <Icon name="error" size="small" />
              <span id={errorId}>A busca por CPF não existe. Busque por nome ou instrumento.</span>
            </div>
          ) : null}
        </div>
        <Button type="submit" variant="secondary" icon="search">
          Buscar
        </Button>
      </form>
      <div className="segmented" role="group" aria-label="Situação">
        {STATUS_OPTIONS.map((option) => (
          <button
            key={option.value}
            type="button"
            className="segmented__option"
            aria-pressed={status === option.value}
            onClick={() => onStatusChange(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  );
}
