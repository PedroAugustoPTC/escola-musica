import { useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { fieldErrorsOf } from '../../components/admin/fieldErrors';
import { SchoolHistoryFilters } from '../../components/admin/SchoolHistoryFilters';
import { SchoolHistoryList } from '../../components/admin/SchoolHistoryList';
import { StudentPicker } from '../../components/admin/StudentPicker';
import { InactiveMark } from '../../components/admin/ui';
import { useSearchParamTerm } from '../../components/admin/useSearchParamTerm';
import { msg } from '../../messages/messages';
import {
  useSchoolHistory,
  useSchoolHistoryTeachers,
  type SchoolHistoryFilters as SchoolHistoryQueryFilters,
  type SchoolReportType,
} from '../../services/admin/schoolHistory';

type RouteKey = 'studentId' | 'teacherId' | 'type' | 'from' | 'to' | 'page' | 'search';

function typeOf(value: string | null): SchoolReportType | undefined {
  return value === 'LESSON' || value === 'SEMESTER' ? value : undefined;
}

/** Filtros da rota: só identificadores, tipo, datas e página (FR-015). */
function filtersOf(params: URLSearchParams): SchoolHistoryQueryFilters {
  return {
    studentId: params.get('studentId') || undefined,
    teacherId: params.get('teacherId') || undefined,
    type: typeOf(params.get('type')),
    from: params.get('from') || undefined,
    to: params.get('to') || undefined,
    page: Math.max(0, Number(params.get('page')) || 0),
  };
}

function totalText(total: number): string {
  // o catálogo escreve o número como {total}; aceita também o modelo posicional {0}
  return msg('SCHOOL_HISTORY_TOTAL', { total }).replace('{0}', String(total));
}

/** A13 — Histórico da escola: relatórios aprovados de qualquer professor e aluno, somente leitura. */
export function SchoolHistoryScreen() {
  const [params, setParams] = useSearchParams();
  const { search, refusedFromUrl, dismissRefusal } = useSearchParamTerm(params, setParams);
  const filters = filtersOf(params);
  const history = useSchoolHistory(filters);
  const teachers = useSchoolHistoryTeachers();
  const data = history.data;
  const filterErrors = fieldErrorsOf(history.error);
  const listStart = useRef<HTMLDivElement>(null);
  const firstRender = useRef(true);

  // ao trocar de página o foco vai para o início da lista
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    listStart.current?.focus();
  }, [filters.page]);

  /** Mudar filtro ou página atualiza a rota com `replace`; mudar filtro volta à primeira página. */
  const update = (changes: Partial<Record<RouteKey, string | undefined>>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === undefined || value === '') next.delete(key);
      else next.set(key, value);
    }
    if (!('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  };

  const clearFilters = () => update({ teacherId: undefined, type: undefined, from: undefined, to: undefined });

  const studentChosen = Boolean(filters.studentId);
  const hasOtherFilters = Boolean(filters.teacherId || filters.type || filters.from || filters.to);

  return (
    <div className="page page--wide">
      <div>
        <h1 className="page-title">{msg('SCHOOL_HISTORY_TITLE')}</h1>
        <p className="subtitle">{msg('SCHOOL_HISTORY_SUBTITLE')}</p>
      </div>

      {studentChosen ? (
        <div className="school-history__student">
          {data?.student ? (
            <>
              <h2 className="card-title">{data.student.name}</h2>
              <InactiveMark active={data.student.active} />
            </>
          ) : null}
          <Button
            variant="secondary"
            size="compact"
            icon="person_search"
            onClick={() => update({ studentId: undefined })}
          >
            {msg('SCHOOL_HISTORY_CHANGE_STUDENT')}
          </Button>
        </div>
      ) : (
        <StudentPicker
          search={search}
          refusedFromUrl={refusedFromUrl}
          onDismissRefusal={dismissRefusal}
          onSearchChange={(term) => update({ search: term, page: undefined })}
          onSelect={(student) => update({ studentId: student.id, search: undefined, page: undefined })}
        />
      )}

      <SchoolHistoryFilters
        applied={{
          teacherId: filters.teacherId ?? '',
          type: filters.type ?? '',
          from: filters.from ?? '',
          to: filters.to ?? '',
        }}
        teachers={teachers.data?.items ?? []}
        errors={filterErrors}
        onApply={(values) =>
          update({
            teacherId: values.teacherId,
            type: values.type,
            from: values.from,
            to: values.to,
          })
        }
        onClear={clearFilters}
      />

      <div ref={listStart} tabIndex={-1} className="school-history__start">
        {history.isPending ? <LoadingState label="Carregando histórico" /> : null}
        {history.isError && filterErrors.length === 0 ? (
          <ErrorAlert error={history.error} onRetry={() => void history.refetch()} />
        ) : null}

        {data && !history.isError && data.items.length === 0 ? (
          <EmptyState
            icon="history"
            text={
              studentChosen && !hasOtherFilters
                ? msg('SCHOOL_HISTORY_EMPTY_STUDENT')
                : msg('SCHOOL_HISTORY_EMPTY_FILTERS')
            }
            action={
              hasOtherFilters ? (
                <Button
                  variant="secondary"
                  size="compact"
                  onClick={clearFilters}
                >
                  {msg('SCHOOL_HISTORY_CLEAR')}
                </Button>
              ) : undefined
            }
          />
        ) : null}

        {data && !history.isError && data.items.length > 0 ? (
          <div className="school-history__results" aria-busy={history.isFetching || undefined}>
            <p role="status" className="support-text">
              {totalText(data.total)}
            </p>
            <SchoolHistoryList
              items={data.items}
              showStudent={!studentChosen}
              pagination={{
                page: data.page,
                size: data.size,
                total: data.total,
                onPage: (page) => update({ page: page > 0 ? String(page) : undefined }),
              }}
            />
          </div>
        ) : null}
      </div>
    </div>
  );
}
