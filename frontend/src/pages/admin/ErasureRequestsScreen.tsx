import { useState } from 'react';
import { Link } from 'react-router';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { Pagination, SegmentedFilter } from '../../components/admin/ui';
import { useErasureRequests, type ErasureStatus } from '../../services/admin/erasures';
import { formatDate } from '../../services/formatting';

type Filter = 'ALL' | ErasureStatus;

const STATUS_TEXT: Record<ErasureStatus, string> = {
  RECEIVED: 'Recebido',
  IN_PROGRESS: 'Arquivos pendentes',
  COMPLETED: 'Concluído',
};

/** A17c — Pedidos de eliminação, com a situação e o caminho para continuar ou ver o comprovante. */
export function ErasureRequestsScreen() {
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<Filter>('ALL');
  const requests = useErasureRequests(page, filter === 'ALL' ? undefined : filter);

  return (
    <div className="page page--medium">
      <div>
        <div className="kicker">Privacidade</div>
        <h1 className="page-title">Pedidos de eliminação</h1>
      </div>
      <SegmentedFilter<Filter>
        label="Situação"
        value={filter}
        onChange={(next) => {
          setFilter(next);
          setPage(0);
        }}
        options={[
          { value: 'ALL', label: 'Todos' },
          { value: 'RECEIVED', label: STATUS_TEXT.RECEIVED },
          { value: 'IN_PROGRESS', label: STATUS_TEXT.IN_PROGRESS },
          { value: 'COMPLETED', label: STATUS_TEXT.COMPLETED },
        ]}
      />
      {requests.isPending ? <LoadingState label="Carregando pedidos" rows={3} /> : null}
      {requests.isError ? <ErrorState error={requests.error} onRetry={() => void requests.refetch()} /> : null}
      {requests.data && requests.data.items.length === 0 ? (
        <EmptyState icon="info" title="Nenhum pedido" text="Nenhum pedido de eliminação nesta situação." />
      ) : null}
      {requests.data && requests.data.items.length > 0 ? (
        <>
          <ul className="admin-list" aria-label="Pedidos de eliminação">
            {requests.data.items.map((item) => (
              <li key={item.id} className="admin-list__item">
                <div>
                  <strong>{item.studentLabel ?? 'Aluno'}</strong> · protocolo {item.protocolReference}
                  <div className="support-text">
                    {STATUS_TEXT[item.status]} · pedido em {formatDate(item.requestedOn)}
                  </div>
                </div>
                {item.status === 'COMPLETED' ? (
                  <Link to={`/admin/erasure-requests/${item.id}/receipt`}>Ver comprovante</Link>
                ) : (
                  <Link to={`/admin/erasure-requests/${item.id}/run`}>Continuar</Link>
                )}
              </li>
            ))}
          </ul>
          <Pagination page={requests.data.page} size={requests.data.size} total={requests.data.total} onPage={setPage}>
            pedido(s)
          </Pagination>
        </>
      ) : null}
    </div>
  );
}
