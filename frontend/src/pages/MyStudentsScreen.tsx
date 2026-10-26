import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Button } from '../components/Button';
import { EmptyState } from '../components/EmptyState';
import { ErrorAlert } from '../components/ErrorState';
import { Layout } from '../components/Layout';
import { CardsSkeleton } from '../components/LoadingState';
import { StudentList } from '../components/student/StudentList';
import { StudentSearchBar } from '../components/student/StudentSearchBar';
import { msg } from '../messages/messages';
import { MY_STUDENTS_PAGE_SIZE, useMyStudents, type StudentStatus } from '../services/myStudents';

/** P01 — Meus alunos: lista paginada dos alunos do professor, com busca e filtro de situação. */
export function MyStudentsScreen() {
  const navigate = useNavigate();
  const [status, setStatus] = useState<StudentStatus>('ACTIVE');
  const [term, setTerm] = useState('');
  const [page, setPage] = useState(0);
  const students = useMyStudents({ status, search: term, page });
  const data = students.data;
  const pages = data ? Math.max(1, Math.ceil(data.total / (data.size || MY_STUDENTS_PAGE_SIZE))) : 1;
  const empty = data !== undefined && data.items.length === 0;
  const firstStudent = empty && !term && status !== 'INACTIVE';

  return (
    <Layout bottomNav>
      <div className="page page--wide">
        <div>
          <div className="kicker">Portal do Professor</div>
          <h1 className="page-title">{msg('MY_STUDENTS_TITLE')}</h1>
          {data && !empty ? <p className="subtitle">{data.total} aluno(s)</p> : null}
        </div>
        {!firstStudent ? (
          <div>
            <Button icon="person_add" onClick={() => navigate('/my-students/new')}>
              Cadastrar aluno
            </Button>
          </div>
        ) : null}
        <StudentSearchBar
          label="Buscar entre os meus alunos"
          term={term}
          status={status}
          onSearch={(value) => {
            setTerm(value);
            setPage(0);
          }}
          onStatusChange={(value) => {
            setStatus(value);
            setPage(0);
          }}
        />
        {students.isPending ? <CardsSkeleton label="Carregando alunos" /> : null}
        {students.isError ? (
          <ErrorAlert error={students.error} onRetry={() => students.refetch()} fromCatalog />
        ) : null}
        {empty && term ? (
          <EmptyState
            icon="person_search"
            text={msg('MY_STUDENTS_SEARCH_EMPTY', { search: term })}
            action={
              <Button
                variant="secondary"
                size="compact"
                onClick={() => {
                  setTerm('');
                  setPage(0);
                }}
              >
                Limpar busca
              </Button>
            }
          />
        ) : null}
        {firstStudent ? (
          <EmptyState
            monogram
            solid
            title="Nenhum aluno por aqui"
            text={msg('MY_STUDENTS_EMPTY')}
            action={
              <Button icon="person_add" onClick={() => navigate('/my-students/new')}>
                Cadastrar o primeiro aluno
              </Button>
            }
          />
        ) : null}
        {empty && !term && !firstStudent ? (
          <EmptyState icon="group_off" text="Nenhum aluno nesta situação." />
        ) : null}
        {data && !empty ? (
          <div aria-busy={students.isFetching || undefined}>
            <StudentList
              variant="teacher"
              label={msg('MY_STUDENTS_TITLE')}
              students={data.items}
              detailPath={(id) => `/my-students/${id}`}
            />
          </div>
        ) : null}
        {data && pages > 1 ? (
          <nav className="pager" aria-label="Paginação">
            <Button
              variant="secondary"
              size="compact"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              Anterior
            </Button>
            <span>
              Página {page + 1} de {pages}
            </span>
            <Button
              variant="secondary"
              size="compact"
              disabled={page + 1 >= pages}
              onClick={() => setPage(page + 1)}
            >
              Próxima
            </Button>
          </nav>
        ) : null}
      </div>
    </Layout>
  );
}
