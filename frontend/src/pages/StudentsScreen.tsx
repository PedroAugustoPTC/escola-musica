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
import { looksLikeCpf } from '../components/student/cpfShape';
import { useStudents } from '../services/students';

const SEARCH_DELAY_MS = 300;

/** Tela 03 — Alunos (03b busca sem resultado, 03c carregando; "Para qual aluno?" ao registrar). */
export function StudentsScreen() {
  const [params, setParams] = useSearchParams();
  const registering = params.get('register') === '1';
  // um `?search=` com forma de CPF é descartado ao ler: não vai à chave de consulta nem à API
  const initialSearch = params.get('search') ?? '';
  const initialRefused = looksLikeCpf(initialSearch.trim());
  const [search, setSearch] = useState(initialRefused ? '' : initialSearch);
  const [refusedFromUrl, setRefusedFromUrl] = useState(initialRefused);
  const [term, setTerm] = useState(initialRefused ? '' : initialSearch);
  const field = useRef<HTMLInputElement>(null);
  const students = useStudents(term);

  const refused = refusedFromUrl || looksLikeCpf(search.trim());

  useEffect(() => {
    // não existe busca por CPF: o termo não chega a virar consulta (nem URL)
    if (looksLikeCpf(search.trim())) return;
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
            aria-invalid={refused ? true : undefined}
            aria-describedby={refused ? 'search-refused' : undefined}
            onChange={(event) => {
              setSearch(event.target.value);
              setRefusedFromUrl(false);
            }}
          />
        </label>
        {refused ? (
          <div id="search-refused" className="field__error" role="alert">
            <Icon name="error" size="small" />
            Não há busca por CPF. Procure pelo nome.
          </div>
        ) : null}
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
