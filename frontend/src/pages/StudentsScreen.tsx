import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { Button } from '../components/Button';
import { StudentCard } from '../components/StudentCard';
import { CardsSkeleton } from '../components/LoadingState';
import { ErrorAlert } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { Layout } from '../components/Layout';
import { Icon } from '../components/Icon';
import { msg } from '../messages/messages';
import { useStudents } from '../services/students';

const SEARCH_DELAY_MS = 300;

/** Tela 03 — Alunos (03b busca sem resultado, 03c carregando; "Para qual aluno?" ao registrar). */
export function StudentsScreen() {
  const [params, setParams] = useSearchParams();
  const registering = params.get('register') === '1';
  const [search, setSearch] = useState(params.get('search') ?? '');
  const [term, setTerm] = useState(search);
  const field = useRef<HTMLInputElement>(null);
  const students = useStudents(term);

  useEffect(() => {
    const wait = window.setTimeout(() => setTerm(search), SEARCH_DELAY_MS);
    return () => window.clearTimeout(wait);
  }, [search]);

  useEffect(() => {
    const nextParams = new URLSearchParams(params);
    if (term.trim()) nextParams.set('search', term.trim());
    else nextParams.delete('search');
    nextParams.delete('focus');
    if (nextParams.toString() !== params.toString()) setParams(nextParams, { replace: true });
  }, [term, params, setParams]);

  useEffect(() => {
    if (params.get('focus') === 'search') field.current?.focus();
  }, [params]);

  const data = students.data;
  const total = data?.result === 'FOUND' ? null : data?.students.length;

  return (
    <Layout activeNav={registering ? 'register' : 'students'} bottomNav>
      <div className="page page--wide">
        <div>
          <div className="kicker">{registering ? msg('REGISTER_LESSON_BUTTON') : 'Alunos'}</div>
          <h1 className="page-title">{registering ? msg('CHOOSE_STUDENT') : 'Seus alunos'}</h1>
          {total !== null && total !== undefined ? (
            <p className="subtitle">{total} aluno(s) associado(s) a você.</p>
          ) : null}
        </div>
        <label className="search">
          <span className="visually-hidden">Buscar aluno por nome</span>
          <Icon name="search" />
          <input
            ref={field}
            type="search"
            className="input"
            value={search}
            placeholder="Buscar aluno por nome"
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
        {students.isPending ? <CardsSkeleton label="Carregando alunos" /> : null}
        {students.isError ? <ErrorAlert error={students.error} onRetry={() => students.refetch()} /> : null}
        {data && data.students.length === 0 ? (
          <EmptyState monogram solid title="Nenhum aluno por aqui" text={msg('NO_STUDENTS')} />
        ) : null}
        {data?.result === 'NONE' && data.students.length > 0 ? (
          <EmptyState
            icon="person_search"
            text={msg('NO_STUDENT_FOUND', { search: term.trim() })}
            action={
              <Button variant="secondary" size="compact" onClick={() => setSearch('')}>
                Limpar busca
              </Button>
            }
          />
        ) : null}
        {data && data.students.length > 0 ? (
          <div className="card-grid" aria-busy={students.isFetching || undefined}>
            {data.students.map((student) => (
              <StudentCard key={student.id} student={student} />
            ))}
          </div>
        ) : null}
      </div>
    </Layout>
  );
}
