import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorState';
import { Icon } from '../../components/Icon';
import { LoadingState } from '../../components/LoadingState';
import { Pagination, SearchForm, SegmentedFilter, StatusBadge } from '../../components/admin/ui';
import { useTeachers, type TeacherActiveFilter } from '../../services/admin/teachers';

const FILTERS: { value: TeacherActiveFilter; label: string }[] = [
  { value: 'all', label: 'Todos' },
  { value: 'active', label: 'Ativos' },
  { value: 'inactive', label: 'Inativos' },
];

function filterOf(value: string | null): TeacherActiveFilter {
  return value === 'active' || value === 'inactive' ? value : 'all';
}

/** A05 — Professores: lista, busca e filtro de situação (nunca mostra código de acesso). */
export function TeachersScreen() {
  const [params, setParams] = useSearchParams();
  const search = params.get('search') ?? '';
  const filter = filterOf(params.get('status'));
  const page = Math.max(0, Number(params.get('page')) || 0);
  const [draft, setDraft] = useState(search);
  const teachers = useTeachers({ search, filter, page });
  const data = teachers.data;

  const update = (changes: { search?: string; status?: TeacherActiveFilter; page?: number }) => {
    const next = new URLSearchParams(params);
    const nextSearch = changes.search ?? search;
    const nextStatus = changes.status ?? filter;
    const nextPage = changes.page ?? 0;
    if (nextSearch.trim()) next.set('search', nextSearch.trim());
    else next.delete('search');
    if (nextStatus !== 'all') next.set('status', nextStatus);
    else next.delete('status');
    if (nextPage > 0) next.set('page', String(nextPage));
    else next.delete('page');
    setParams(next);
  };

  const hasCriteria = search.trim() !== '' || filter !== 'all';

  return (
    <div className="page page--wide">
      <div className="admin-page-head">
        <div>
          <div className="kicker">Cadastros</div>
          <h1 className="page-title">Professores</h1>
        </div>
        <Link to="/admin/teachers/new" className="button button--primary">
          <Icon name="person_add" size="medium" />
          Cadastrar professor
        </Link>
      </div>
      <div className="admin-toolbar">
        <SearchForm
          label="Buscar professor"
          value={draft}
          onChange={setDraft}
          onSubmit={() => update({ search: draft })}
        />
        <SegmentedFilter
          label="Situação"
          value={filter}
          options={FILTERS}
          onChange={(value) => update({ status: value })}
        />
      </div>

      {teachers.isPending ? <LoadingState label="Carregando professores" /> : null}
      {teachers.isError ? (
        <ErrorAlert error={teachers.error} onRetry={() => void teachers.refetch()} />
      ) : null}

      {data && data.items.length === 0 ? (
        hasCriteria ? (
          <EmptyState
            icon="person_search"
            text={`Nenhum professor encontrado${search.trim() ? ` para “${search.trim()}”` : ''}.`}
            action={
              <Button
                variant="secondary"
                size="compact"
                onClick={() => {
                  setDraft('');
                  setParams(new URLSearchParams());
                }}
              >
                Limpar busca
              </Button>
            }
          />
        ) : (
          <EmptyState
            icon="school"
            text="Nenhum professor cadastrado ainda."
            action={
              <Link to="/admin/teachers/new" className="button button--primary">
                Cadastrar professor
              </Link>
            }
          />
        )
      ) : null}

      {data && data.items.length > 0 ? (
        <>
          <ul className="admin-list" aria-busy={teachers.isFetching || undefined}>
            {data.items.map((teacher) => (
              <li key={teacher.id} className="admin-list__item">
                <div className="admin-list__main">
                  <Link to={`/admin/teachers/${teacher.id}`} className="admin-list__title">
                    {teacher.name}
                  </Link>
                  <div className="admin-list__meta">
                    <span className="admin-list__meta-item">{teacher.email}</span>
                    <StatusBadge active={teacher.active} />
                    {teacher.telegramLinked ? (
                      <span className="admin-list__meta-item">
                        <Icon name="link" size="small" />
                        Telegram vinculado
                      </span>
                    ) : null}
                  </div>
                </div>
              </li>
            ))}
          </ul>
          <Pagination
            page={data.page}
            size={data.size}
            total={data.total}
            onPage={(next) => update({ page: next })}
          >
            professor(es)
          </Pagination>
        </>
      ) : null}
    </div>
  );
}
