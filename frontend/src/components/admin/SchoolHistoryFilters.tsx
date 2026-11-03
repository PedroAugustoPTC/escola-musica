import { useId, useState } from 'react';
import { Button } from '../Button';
import { Icon } from '../Icon';
import { msg } from '../../messages/messages';
import type { Teacher } from '../../services/admin/teachers';
import type { SchoolReportType } from '../../services/admin/schoolHistory';
import { errorOfField, fieldErrorText, type FieldError } from './fieldErrors';
import { TextField } from './ui';
import { useCompactLayout } from './useCompactLayout';

/** Valores dos filtros; `''` significa "sem filtro". */
export interface SchoolHistoryFilterValues {
  teacherId: string;
  type: SchoolReportType | '';
  from: string;
  to: string;
}

export const NO_FILTERS: SchoolHistoryFilterValues = { teacherId: '', type: '', from: '', to: '' };

/** Quantos filtros estão ativos (período conta como um só quando tem as duas pontas, senão cada ponta conta). */
export function activeFilterCount(values: SchoolHistoryFilterValues): number {
  return [values.teacherId, values.type, values.from, values.to].filter((value) => value !== '').length;
}

interface SchoolHistoryFiltersProps {
  /** Filtros aplicados (vêm da rota). */
  applied: SchoolHistoryFilterValues;
  /** Professores para a lista (ativos e inativos). */
  teachers: Teacher[];
  /** Violações de campo devolvidas pelo servidor (`details.fields`). */
  errors: FieldError[];
  onApply: (values: SchoolHistoryFilterValues) => void;
  onClear: () => void;
}

/**
 * Filtros do histórico: professor (inativos marcados), tipo e período (De/Até). Recolhíveis abaixo de 640 px,
 * com contador de filtros ativos. Só "Aplicar" muda a rota; o rascunho vive aqui.
 */
export function SchoolHistoryFilters({
  applied,
  teachers,
  errors,
  onApply,
  onClear,
}: SchoolHistoryFiltersProps) {
  const compact = useCompactLayout();
  const panelId = useId();
  const teacherId = useId();
  const typeId = useId();
  const [draft, setDraft] = useState<SchoolHistoryFilterValues>(applied);
  const [expanded, setExpanded] = useState(errors.length > 0);

  // a rota é a fonte da verdade: ao mudar (Limpar, voltar do navegador), o rascunho acompanha
  // (ajuste durante a renderização, sem efeito)
  const appliedKey = [applied.teacherId, applied.type, applied.from, applied.to].join('|');
  const [seenKey, setSeenKey] = useState(appliedKey);
  if (seenKey !== appliedKey) {
    setSeenKey(appliedKey);
    setDraft(applied);
  }

  // erro de filtro abre o painel recolhido para o campo ficar visível
  const [seenErrors, setSeenErrors] = useState(errors.length);
  if (seenErrors !== errors.length) {
    setSeenErrors(errors.length);
    if (errors.length > 0) setExpanded(true);
  }

  const count = activeFilterCount(applied);
  const open = !compact || expanded;
  const typeError = errorOfField(errors, 'type');
  const fromError = errorOfField(errors, 'from');
  const toError = errorOfField(errors, 'to');

  const change = (changes: Partial<SchoolHistoryFilterValues>) => setDraft((current) => ({ ...current, ...changes }));

  return (
    <section className="school-history__filters" aria-label="Filtros">
      {compact ? (
        <Button
          variant="secondary"
          size="compact"
          icon="expand_more"
          aria-expanded={expanded}
          aria-controls={panelId}
          onClick={() => setExpanded((value) => !value)}
        >
          {count > 0 ? msg(count === 1 ? 'SCHOOL_HISTORY_FILTERS_BUTTON_ONE' : 'SCHOOL_HISTORY_FILTERS_BUTTON_MANY', { count }) : 'Filtros'}
        </Button>
      ) : count > 0 ? (
        <p className="support-text">
          {msg(count === 1 ? 'SCHOOL_HISTORY_FILTERS_ACTIVE_ONE' : 'SCHOOL_HISTORY_FILTERS_ACTIVE_MANY', { count })}
        </p>
      ) : null}

      <form
        id={panelId}
        hidden={!open}
        className="school-history__filters-form"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          onApply(draft);
        }}
      >
        <div className="field">
          <label htmlFor={teacherId} className="field__label">
            {msg('SCHOOL_HISTORY_FILTER_TEACHER')}
          </label>
          <select
            id={teacherId}
            className="input"
            value={draft.teacherId}
            onChange={(event) => change({ teacherId: event.target.value })}
          >
            <option value="">{msg('SCHOOL_HISTORY_TYPE_ALL')}</option>
            {teachers.map((teacher) => (
              <option key={teacher.id} value={teacher.id}>
                {teacher.active ? teacher.name : `${teacher.name} (inativo)`}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label htmlFor={typeId} className="field__label">
            {msg('SCHOOL_HISTORY_FILTER_TYPE')}
          </label>
          <select
            id={typeId}
            className="input"
            value={draft.type}
            aria-invalid={typeError ? true : undefined}
            aria-describedby={typeError ? `${typeId}-error` : undefined}
            onChange={(event) => change({ type: event.target.value as SchoolHistoryFilterValues['type'] })}
          >
            <option value="">{msg('SCHOOL_HISTORY_TYPE_ALL')}</option>
            <option value="LESSON">{msg('SCHOOL_HISTORY_TYPE_LESSON')}</option>
            <option value="SEMESTER">{msg('SCHOOL_HISTORY_TYPE_SEMESTER')}</option>
          </select>
          {typeError ? (
            <div id={`${typeId}-error`} className="field__error">
              <Icon name="error" size="small" />
              {fieldErrorText(typeError.code)}
            </div>
          ) : null}
        </div>

        <TextField
          label={msg('SCHOOL_HISTORY_FILTER_FROM')}
          type="date"
          value={draft.from}
          error={fromError ? fieldErrorText(fromError.code) : undefined}
          onChange={(event) => change({ from: event.target.value })}
        />
        <TextField
          label={msg('SCHOOL_HISTORY_FILTER_TO')}
          type="date"
          value={draft.to}
          error={toError ? fieldErrorText(toError.code) : undefined}
          onChange={(event) => change({ to: event.target.value })}
        />

        <div className="admin-actions school-history__filters-actions">
          <Button type="submit" size="compact">
            {msg('SCHOOL_HISTORY_APPLY')}
          </Button>
          <Button variant="secondary" size="compact" onClick={onClear}>
            {msg('SCHOOL_HISTORY_CLEAR')}
          </Button>
        </div>
      </form>
    </section>
  );
}
