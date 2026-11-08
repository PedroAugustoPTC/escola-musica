import { useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorState';
import { Icon } from '../../components/Icon';
import { LoadingState } from '../../components/LoadingState';
import { hasMessage, msg } from '../../messages/messages';
import {
  useAcknowledgeAlert,
  useOperationalAlerts,
  type EffectivePolicy,
  type OperationalAlert,
} from '../../services/admin/operations';
import { instantDate, instantTime } from '../../services/formatting';

function typeLabel(type: string): string {
  const key = `ALERT_TYPE_${type}`;
  return hasMessage(key) ? msg(key) : type;
}

function when(instant: string): string {
  return `${instantDate(instant)} ${instantTime(instant)}`;
}

function AlertTable({
  title,
  alerts,
  acknowledge,
  pendingId,
}: {
  title: string;
  alerts: OperationalAlert[];
  acknowledge: (id: string) => void;
  pendingId: string | null;
}) {
  return (
    <div className="costs__table-wrap" role="region" aria-label={title} tabIndex={0}>
      <table className="school-history__table">
        <caption className="visually-hidden">{title}</caption>
        <thead>
          <tr>
            <th scope="col">{msg('ALERT_COLUMN_TYPE')}</th>
            <th scope="col">{msg('ALERT_COLUMN_COMPONENT')}</th>
            <th scope="col">{msg('ALERT_COLUMN_STARTED')}</th>
            <th scope="col">{msg('ALERT_COLUMN_LAST_SEEN')}</th>
            <th scope="col" className="costs__numeric">
              {msg('ALERT_COLUMN_OCCURRENCES')}
            </th>
            <th scope="col">{msg('ALERT_COLUMN_STATUS')}</th>
            <th scope="col">{msg('ALERT_COLUMN_ACTIONS')}</th>
          </tr>
        </thead>
        <tbody>
          {alerts.map((alert) => (
            <tr key={alert.id}>
              <th scope="row">{typeLabel(alert.type)}</th>
              <td>{alert.component}</td>
              <td>{when(alert.startedAt)}</td>
              <td>{when(alert.lastSeenAt)}</td>
              <td className="costs__numeric">{alert.occurrences}</td>
              <td>
                <span className={`status-badge status-badge--${alert.status === 'ACTIVE' ? 'pending' : 'active'}`}>
                  <Icon name={alert.status === 'ACTIVE' ? 'warning' : 'check_circle'} size="small" />
                  {msg(`ALERT_STATUS_${alert.status}`)}
                </span>
              </td>
              <td>
                {alert.status === 'ACTIVE' && alert.type === 'AUDIT_CHAIN_BROKEN' ? (
                  <Button
                    variant="secondary"
                    size="compact"
                    disabled={pendingId === alert.id}
                    onClick={() => acknowledge(alert.id)}
                  >
                    {msg('ALERT_ACK_BUTTON')}
                  </Button>
                ) : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PolicyList({ policy }: { policy: EffectivePolicy }) {
  return (
    <section aria-labelledby="alerts-policy" tabIndex={0}>
      <h2 className="card-title" id="alerts-policy">
        {msg('ALERTS_POLICY_TITLE')}
      </h2>
      <dl className="alerts__policy">
        {Object.entries(policy).map(([key, entry]) => (
          <div key={key} className="alerts__policy-row">
            <dt>{hasMessage(`ALERT_POLICY_${key}`) ? msg(`ALERT_POLICY_${key}`) : key}</dt>
            <dd>
              {msg('ALERTS_POLICY_VALUE', {
                value: entry.value,
                source: msg(`ALERT_POLICY_SOURCE_${entry.source}`),
              })}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

/**
 * A18 — Alertas de operação (FR-055, FR-056): ativos no topo, histórico abaixo, os prazos e
 * limites em vigor e, só para a quebra da trilha, "Marcar como apurado". Sem dado pessoal.
 */
export function AlertsScreen() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page')) || 0);
  const alerts = useOperationalAlerts(page);
  const acknowledge = useAcknowledgeAlert();
  const start = useRef<HTMLDivElement>(null);
  const firstRender = useRef(true);
  const data = alerts.data;

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    start.current?.focus();
  }, [page]);

  const goTo = (target: number) => {
    const next = new URLSearchParams(params);
    if (target > 0) next.set('page', String(target));
    else next.delete('page');
    setParams(next, { replace: true });
  };

  const active = data?.items.filter((alert) => alert.status === 'ACTIVE') ?? [];
  const history = data?.items.filter((alert) => alert.status !== 'ACTIVE') ?? [];

  return (
    <div className="page page--wide">
      <div>
        <h1 className="page-title">{msg('ALERTS_TITLE')}</h1>
        <p className="subtitle">{msg('ALERTS_SUBTITLE')}</p>
      </div>

      <div ref={start} tabIndex={-1} className="school-history__start">
        {alerts.isPending ? <LoadingState label={msg('ALERTS_LOADING')} /> : null}
        {alerts.isError ? <ErrorAlert error={alerts.error} onRetry={() => void alerts.refetch()} /> : null}
        {acknowledge.isError ? <ErrorAlert error={acknowledge.error} /> : null}
        {acknowledge.isSuccess ? (
          <p role="status" className="support-text">
            {msg('ALERT_ACK_DONE')}
          </p>
        ) : null}

        {data && !alerts.isError ? (
          <div aria-busy={alerts.isFetching || undefined}>
            <section aria-labelledby="alerts-active">
              <h2 className="card-title" id="alerts-active">
                {msg('ALERTS_ACTIVE_TITLE')}
              </h2>
              {active.length === 0 ? (
                <EmptyState icon="check_circle" text={msg('ALERTS_NONE_ACTIVE')} />
              ) : (
                <>
                  <AlertTable
                    title={msg('ALERTS_ACTIVE_TITLE')}
                    alerts={active}
                    acknowledge={(id) => acknowledge.mutate(id)}
                    pendingId={acknowledge.isPending ? (acknowledge.variables ?? null) : null}
                  />
                  {active.some((alert) => alert.type === 'AUDIT_CHAIN_BROKEN') ? (
                    <p className="support-text">{msg('ALERT_ACK_HELP')}</p>
                  ) : null}
                </>
              )}
            </section>

            <section aria-labelledby="alerts-history">
              <h2 className="card-title" id="alerts-history">
                {msg('ALERTS_HISTORY_TITLE')}
              </h2>
              {history.length === 0 ? (
                <EmptyState icon="history" text={msg('ALERTS_NONE_HISTORY')} />
              ) : (
                <AlertTable
                  title={msg('ALERTS_HISTORY_TITLE')}
                  alerts={history}
                  acknowledge={(id) => acknowledge.mutate(id)}
                  pendingId={null}
                />
              )}
            </section>

            {data.total > data.size ? (
              <nav aria-label="Paginação" className="admin-pagination">
                <span className="support-text">
                  {data.page * data.size + 1}–{Math.min((data.page + 1) * data.size, data.total)} de {data.total}
                </span>
                <div className="admin-actions">
                  <Button variant="secondary" size="compact" disabled={data.page <= 0} onClick={() => goTo(data.page - 1)}>
                    Anterior
                  </Button>
                  <Button
                    variant="secondary"
                    size="compact"
                    disabled={(data.page + 1) * data.size >= data.total}
                    onClick={() => goTo(data.page + 1)}
                  >
                    Próxima
                  </Button>
                </div>
              </nav>
            ) : null}

            <PolicyList policy={data.effectivePolicy} />
          </div>
        ) : null}
      </div>
    </div>
  );
}
