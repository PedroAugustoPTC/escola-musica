import { useState } from 'react';
import { Link } from 'react-router';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { Pagination } from '../../components/admin/ui';
import { usePendingConsents, type PendingReason } from '../../services/admin/consents';

const REASON_TEXT: Record<PendingReason, string> = {
  MISSING: 'Sem consentimento registrado',
  REVOKED: 'Consentimento revogado',
  SUPERSEDED: 'Termo novo exige novo consentimento',
};

/** A16c — Alunos menores sem consentimento válido; em operação normal a lista fica vazia. */
export function PendingConsentsScreen() {
  const [page, setPage] = useState(0);
  const pending = usePendingConsents(page);

  return (
    <div className="page page--medium">
      <div>
        <div className="kicker">Consentimento</div>
        <h1 className="page-title">Consentimentos pendentes</h1>
        <p className="support-text">
          Alunos menores de 18 anos sem consentimento válido ficam sem relatório gerado por IA até o consentimento ser
          registrado.
        </p>
      </div>
      {pending.isPending ? <LoadingState label="Carregando pendências" rows={3} /> : null}
      {pending.isError ? <ErrorState error={pending.error} onRetry={() => void pending.refetch()} /> : null}
      {pending.data && pending.data.items.length === 0 ? (
        <EmptyState icon="verified_user" title="Nenhuma pendência" text="Todos os alunos menores têm consentimento válido." />
      ) : null}
      {pending.data && pending.data.items.length > 0 ? (
        <>
          <ul className="admin-list" aria-label="Alunos com consentimento pendente">
            {pending.data.items.map((item) => (
              <li key={item.studentId} className="admin-list__item">
                <div>
                  <Link to={`/admin/students/${item.studentId}`}>{item.studentName}</Link>
                  <div className="support-text">{REASON_TEXT[item.reason]}</div>
                </div>
              </li>
            ))}
          </ul>
          <Pagination page={pending.data.page} size={pending.data.size} total={pending.data.total} onPage={setPage}>
            aluno(s)
          </Pagination>
        </>
      ) : null}
    </div>
  );
}
