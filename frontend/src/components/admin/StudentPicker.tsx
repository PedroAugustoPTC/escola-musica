import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { Icon } from '../Icon';
import { msg } from '../../messages/messages';
import { looksLikeCpf } from '../student/cpfShape';
import { useAdminStudents, type AdminStudent } from '../../services/admin/students';
import { InactiveMark } from './ui';

const CPF_REFUSED = 'Não há busca por CPF. Procure pelo nome ou pelo instrumento.';
const TYPING_DELAY_MS = 250;

interface StudentPickerProps {
  /** Termo confirmado (vem da rota, via `useSearchParamTerm`). */
  search: string;
  /** O termo da rota tinha forma de CPF e foi descartado. */
  refusedFromUrl: boolean;
  onDismissRefusal: () => void;
  /** Chamado, depois de uma pausa na digitação, com o termo aparado. */
  onSearchChange: (term: string) => void;
  onSelect: (student: AdminStudent) => void;
}

/**
 * Combobox acessível de busca de aluno (todas as situações). Termo com forma de CPF é recusado antes de
 * chegar à rota, à API ou à chave de consulta.
 */
export function StudentPicker({
  search,
  refusedFromUrl,
  onDismissRefusal,
  onSearchChange,
  onSelect,
}: StudentPickerProps) {
  const inputId = useId();
  const listId = useId();
  const errorId = useId();
  const [draft, setDraft] = useState(search);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [cpfRefused, setCpfRefused] = useState(false);
  const timer = useRef<number | undefined>(undefined);

  useEffect(() => () => window.clearTimeout(timer.current), []);

  const term = search.trim();
  const students = useAdminStudents({ search, status: 'ALL', page: 0, enabled: term !== '' });
  const items = term !== '' ? (students.data?.items ?? []) : [];
  const listOpen = open && items.length > 0;
  const refused = cpfRefused || refusedFromUrl;

  const change = (value: string) => {
    setDraft(value);
    setOpen(true);
    setActive(-1);
    onDismissRefusal();
    window.clearTimeout(timer.current);
    if (looksLikeCpf(value.trim())) {
      setCpfRefused(true);
      return;
    }
    setCpfRefused(false);
    timer.current = window.setTimeout(() => onSearchChange(value.trim()), TYPING_DELAY_MS);
  };

  const choose = (student: AdminStudent) => {
    window.clearTimeout(timer.current);
    setOpen(false);
    setActive(-1);
    onSelect(student);
  };

  const keyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown' && items.length > 0) {
      event.preventDefault();
      setOpen(true);
      setActive((current) => (current + 1) % items.length);
    } else if (event.key === 'ArrowUp' && items.length > 0) {
      event.preventDefault();
      setOpen(true);
      setActive((current) => (current <= 0 ? items.length - 1 : current - 1));
    } else if (event.key === 'Enter' && listOpen && active >= 0) {
      event.preventDefault();
      choose(items[active]!);
    } else if (event.key === 'Escape') {
      setOpen(false);
      setActive(-1);
    }
  };

  const optionId = (index: number) => `${listId}-option-${index}`;
  const noResult = open && term !== '' && students.isSuccess && items.length === 0 && !refused;

  return (
    <div className="field student-picker">
      <label htmlFor={inputId} className="field__label">
        {msg('SCHOOL_HISTORY_STUDENT_SEARCH')}
      </label>
      <div className="search">
        <Icon name="search" />
        <input
          id={inputId}
          type="text"
          role="combobox"
          className="input"
          value={draft}
          maxLength={100}
          autoComplete="off"
          autoCapitalize="off"
          spellCheck={false}
          aria-expanded={listOpen}
          aria-controls={listOpen ? listId : undefined}
          aria-autocomplete="list"
          aria-activedescendant={listOpen && active >= 0 ? optionId(active) : undefined}
          aria-invalid={refused ? true : undefined}
          aria-describedby={refused ? errorId : undefined}
          onChange={(event) => change(event.target.value)}
          onKeyDown={keyDown}
          onBlur={() => setOpen(false)}
          onFocus={() => setOpen(true)}
        />
      </div>
      {refused ? (
        <div id={errorId} className="field__error">
          <Icon name="error" size="small" />
          {CPF_REFUSED}
        </div>
      ) : null}
      {listOpen ? (
        <ul
          id={listId}
          role="listbox"
          aria-label={msg('SCHOOL_HISTORY_STUDENT_SEARCH')}
          className="student-picker__list"
        >
          {items.map((student, index) => (
            <li
              key={student.id}
              id={optionId(index)}
              role="option"
              aria-selected={index === active}
              className={`student-picker__option${index === active ? ' student-picker__option--active' : ''}`}
              // mantém o foco no campo ao clicar
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => choose(student)}
            >
              <span className="student-picker__name">{student.name}</span>
              <InactiveMark active={student.active} />
              {student.teachers && student.teachers.length > 0 ? (
                <span className="support-text">
                  Professores:{' '}
                  {student.teachers.map((t) => (t.active ? t.name : `${t.name} (inativo)`)).join(', ')}
                </span>
              ) : null}
            </li>
          ))}
        </ul>
      ) : null}
      {noResult ? (
        <p role="status" className="support-text">
          Nenhum aluno encontrado para “{term}”.
        </p>
      ) : null}
    </div>
  );
}
