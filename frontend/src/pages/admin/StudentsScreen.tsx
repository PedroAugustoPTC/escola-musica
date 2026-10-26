import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorState';
import { Icon } from '../../components/Icon';
import { LoadingState } from '../../components/LoadingState';
import { Pagination, SearchForm, SegmentedFilter, StatusBadge } from '../../components/admin/ui';
import { useSearchParamTerm } from '../../components/admin/useSearchParamTerm';
import { looksLikeCpf } from '../../components/student/cpfShape';
import { useAdminStudents, type StudentStatus } from '../../services/admin/students';

const CPF_REFUSED = 'Não há busca por CPF. Procure pelo nome ou pelo instrumento.';

const FILTERS: { value: StudentStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Ativos' },
  { value: 'INACTIVE', label: 'Inativos' },
  { value: 'ALL', label: 'Todos' },
];

function statusOf(value: string | null): StudentStatus {
  return value === 'INACTIVE' || value === 'ALL' ? value : 'ACTIVE';
}

/** A10 — Alunos da escola: busca (nome ou instrumento), situação, paginação e professores associados. */
export function StudentsScreen() {
  const [params, setParams] = useSearchParams();
  // um `?search=` com forma de CPF é descartado ao ler: não entra na chave de consulta nem na API
  const { search, refusedFromUrl, dismissRefusal } = useSearchParamTerm(params, setParams);
  const status = statusOf(params.get('status'));
  const page = Math.max(0, Number(params.get('page')) || 0);
  const [draft, setDraft] = useState(search);
  const [searchError, setSearchError] = useState('');
  const students = useAdminStudents({ search, status, page });
  const data = students.data;

  const update = (changes: { search?: string; status?: StudentStatus; page?: number }) => {
    const next = new URLSearchParams(params);
    const nextSearch = changes.search ?? search;
    const nextStatus = changes.status ?? status;
    const nextPage = changes.page ?? 0;
    if (nextSearch.trim()) next.set('search', nextSearch.trim());
    else next.delete('search');
    if (nextStatus !== 'ACTIVE') next.set('status', nextStatus);
    else next.delete('status');
    if (nextPage > 0) next.set('page', String(nextPage));
    else next.delete('page');
    setParams(next);
  };

  const submitSearch = () => {
    // não existe busca por CPF: recusado aqui, sem ir à URL, à API nem à chave de consulta
    if (looksLikeCpf(draft.trim())) {
      setSearchError(CPF_REFUSED);
      return;
    }
    setSearchError('');
    dismissRefusal();
    update({ search: draft });
  };

  const hasCriteria = search.trim() !== '' || status !== 'ACTIVE';

  return (
    <div className="page page--wide">
      <div className="admin-page-head">
        <div>
          <div className="kicker">Cadastros</div>
          <h1 className="page-title">Alunos</h1>
        </div>
        <Link to="/admin/students/new" className="button button--primary">
          <Icon name="person_add" size="medium" />
          Cadastrar aluno
        </Link>
      </div>
      <div className="admin-toolbar">
        <SearchForm
          label="Buscar aluno"
          value={draft}
          onChange={(value) => {
            setDraft(value);
            setSearchError('');
            dismissRefusal();
          }}
          onSubmit={submitSearch}
          error={searchError || (refusedFromUrl ? CPF_REFUSED : '')}
        />
        <SegmentedFilter
          label="Situação"
          value={status}
          options={FILTERS}
          onChange={(value) => update({ status: value })}
        />
      </div>

      {students.isPending ? <LoadingState label="Carregando alunos" /> : null}
      {students.isError ? (
        <ErrorAlert error={students.error} onRetry={() => void students.refetch()} />
      ) : null}

      {data && data.items.length === 0 ? (
        hasCriteria ? (
          <EmptyState
            icon="person_search"
            text={`Nenhum aluno encontrado${search.trim() ? ` para “${search.trim()}”` : ''}.`}
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
            icon="group"
            text="Nenhum aluno cadastrado ainda."
            action={
              <Link to="/admin/students/new" className="button button--primary">
                Cadastrar aluno
              </Link>
            }
          />
        )
      ) : null}

      {data && data.items.length > 0 ? (
        <>
          <ul className="admin-list" aria-busy={students.isFetching || undefined}>
            {data.items.map((student) => (
              <li key={student.id} className="admin-list__item">
                <div className="admin-list__main">
                  <Link to={`/admin/students/${student.id}`} className="admin-list__title">
                    {student.name}
                  </Link>
                  <div className="admin-list__meta">
                    {student.age !== undefined ? (
                      <span className="admin-list__meta-item">{student.age} anos</span>
                    ) : null}
                    {student.instrument ? (
                      <span className="admin-list__meta-item">{student.instrument}</span>
                    ) : null}
                    <StatusBadge active={student.active} />
                  </div>
                  {student.teachers && student.teachers.length > 0 ? (
                    <div className="admin-list__meta">
                      <span className="admin-list__meta-item">
                        <Icon name="school" size="small" />
                        Professores:{' '}
                        {student.teachers
                          .map((teacher) => (teacher.active ? teacher.name : `${teacher.name} (inativo)`))
                          .join(', ')}
                      </span>
                    </div>
                  ) : null}
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
            aluno(s)
          </Pagination>
        </>
      ) : null}
    </div>
  );
}
